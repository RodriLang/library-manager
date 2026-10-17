package com.rodrilang.librarymanager.shipping.oca;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.shipping.model.BookstoreShippingSettings;
import com.rodrilang.librarymanager.shipping.model.ShippingDeliveryType;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.operations.ReturnShippingClient;
import com.rodrilang.librarymanager.shipping.operations.ReturnShippingLabelClient;
import com.rodrilang.librarymanager.shipping.operations.ReturnShippingResult;
import com.rodrilang.librarymanager.shipping.repository.BookstoreShippingSettingsRepository;
import com.rodrilang.librarymanager.shipping.returns.model.StoreReturn;
import com.rodrilang.librarymanager.shipping.returns.repository.StoreReturnItemRepository;
import com.rodrilang.librarymanager.shipping.service.BookstoreShippingConfigurationService;
import com.rodrilang.librarymanager.shipping.shipment.provider.ShipmentLabelResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class OcaReturnShippingClient implements ReturnShippingClient, ReturnShippingLabelClient {
    private final OcaClientSupport support;
    private final OcaSettingsReader settingsReader;
    private final BookstoreShippingConfigurationService configurationService;
    private final BookstoreShippingSettingsRepository settingsRepository;
    private final StoreReturnItemRepository returnItemRepository;

    @Override
    public ShippingProviderType provider() {
        return ShippingProviderType.OCA;
    }

    @Override
    public ReturnShippingResult createReturn(StoreReturn storeReturn) {
        if (storeReturn.getOrder().getShippingDeliveryType() == ShippingDeliveryType.BRANCH
                || storeReturn.getOrder().getShippingDeliveryType() == ShippingDeliveryType.PICKUP_POINT) {
            throw new BusinessException("La devolución automática OCA requiere una dirección de origen del cliente. Para pedidos retirados en sucursal cargá el seguimiento de devolución manualmente.");
        }
        if (blank(storeReturn.getOrder().getShippingDestinationStreet()) || blank(storeReturn.getOrder().getShippingDestinationStreetNumber())
                || blank(storeReturn.getOrder().getShippingDestinationPostalCode())) {
            throw new BusinessException("El pedido no tiene una dirección de entrega completa para generar la logística inversa de OCA.");
        }

        Long bookstoreId = storeReturn.getBookstore().getId();
        Map<String, String> credentials = configurationService.requireCredentials(bookstoreId, provider());
        Map<String, Object> settings = settingsReader.settings(bookstoreId);
        String user = support.require(credentials, "usuario", "usuario e-Pak");
        String password = support.require(credentials, "password", "contraseña e-Pak");
        String account = support.requireSetting(settings, credentials, "nroCuenta", "número de cuenta");
        String costCenter = first(text(settings.get("centroCosto")), credentials.get("centroCosto"));
        if (blank(costCenter)) throw new BusinessException("Configurá el centro de costo de OCA (centroCosto).");
        int operation = reverseOperation(credentials, settings);

        BookstoreShippingSettings destination = settingsRepository.findByBookstoreId(bookstoreId)
                .orElseThrow(() -> new BusinessException("Configurá el origen de envíos de la librería antes de generar devoluciones."));
        if (blank(destination.getOriginStreet()) || blank(destination.getOriginStreetNumber()) || blank(destination.getOriginPostalCode())) {
            throw new BusinessException("Completá la dirección de la librería antes de generar devoluciones con OCA.");
        }

        PackageEstimate pack = estimate(storeReturn);
        String xml = buildXml(account, operation, costCenter, storeReturn, destination, pack, settings);
        boolean confirm = Boolean.parseBoolean(Objects.toString(settings.getOrDefault("confirmarRetiroDevolucion", "true")));
        String response = support.post(settings, "IngresoORMultiplesRetiros_v2", Map.of(
                "usr", user,
                "psw", password,
                "XML_Datos", xml,
                "ConfirmarRetiro", String.valueOf(confirm).toLowerCase(Locale.ROOT),
                "ArchivoCliente", "",
                "ArchivoProceso", ""
        ));
        Document document = OcaXmlSupport.parse(response);
        List<Element> details = OcaXmlSupport.elements(document, "DetalleIngresos");
        if (details.isEmpty()) throw new BusinessException("OCA no devolvió el detalle de la logística inversa creada.");
        Element detail = details.get(0);
        String externalId = OcaXmlSupport.text(detail, "OrdenRetiro");
        String tracking = OcaXmlSupport.text(detail, "NumeroEnvio");
        String status = OcaXmlSupport.text(detail, "Estado");
        if (blank(tracking)) throw new BusinessException("OCA no devolvió el número de la devolución. Estado: " + Objects.toString(status, "sin detalle"));
        return new ReturnShippingResult(externalId, tracking, support.trackingUrl(tracking), null);
    }

    @Override
    public ShipmentLabelResult label(StoreReturn storeReturn) {
        if (blank(storeReturn.getTrackingNumber()) && blank(storeReturn.getProviderReturnId())) {
            throw new BusinessException("La devolución todavía no tiene un número de OCA.");
        }
        Map<String, Object> settings = settingsReader.settings(storeReturn.getBookstore().getId());
        String operation = "10x15".equalsIgnoreCase(Objects.toString(settings.getOrDefault("labelFormat", "10x15")))
                ? "GetPdfDeEtiquetasPorOrdenOrNumeroEnvioParaEtiquetadora"
                : "GetPdfDeEtiquetasPorOrdenOrNumeroEnvio";
        String response = support.post(settings, operation, Map.of(
                "idOrdenRetiro", Objects.toString(storeReturn.getProviderReturnId(), ""),
                "nroEnvio", Objects.toString(storeReturn.getTrackingNumber(), ""),
                "logisticaInversa", "true"
        ));
        Document document = OcaXmlSupport.parse(response);
        String base64 = document.getDocumentElement().getTextContent();
        if (blank(base64)) throw new BusinessException("OCA no devolvió el PDF de la etiqueta de devolución.");
        try {
            byte[] pdf = Base64.getMimeDecoder().decode(base64.trim());
            return new ShipmentLabelResult(pdf, "oca-devolucion-" + Objects.toString(storeReturn.getTrackingNumber(), storeReturn.getId().toString()) + ".pdf", "application/pdf");
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("OCA devolvió una etiqueta de devolución en un formato no reconocido.");
        }
    }

    private int reverseOperation(Map<String, String> credentials, Map<String, Object> settings) {
        String value = first(text(settings.get("operativaDevolucion")), credentials.get("operativaDevolucion"),
                text(settings.get("operativaLogisticaInversa")), credentials.get("operativaLogisticaInversa"));
        if (blank(value)) throw new BusinessException("Configurá la operativa de logística inversa de OCA (operativaDevolucion).");
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException ex) { throw new BusinessException("La operativa de devolución de OCA no es válida."); }
    }

    private PackageEstimate estimate(StoreReturn storeReturn) {
        BigDecimal weight = BigDecimal.ZERO;
        BigDecimal width = BigDecimal.ZERO;
        BigDecimal height = BigDecimal.ZERO;
        BigDecimal depth = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        var items = returnItemRepository.findAllByStoreReturnIdOrderByIdAsc(storeReturn.getId());
        for (var item : items) {
            var book = item.getOrderItem().getInventory().getBook();
            int quantity = item.getQuantity();
            BigDecimal w = positive(book.getWeightGrams(), new BigDecimal("400"));
            BigDecimal wi = positive(book.getWidthCm(), new BigDecimal("17"));
            BigDecimal h = positive(book.getHeightCm(), new BigDecimal("24"));
            BigDecimal d = positive(book.getDepthCm(), new BigDecimal("2.5"));
            weight = weight.add(w.multiply(BigDecimal.valueOf(quantity)));
            width = width.max(wi); height = height.max(h); depth = depth.max(d);
            volume = volume.add(wi.multiply(h).multiply(d).multiply(BigDecimal.valueOf(quantity)));
        }
        BigDecimal packagingWeight = weight.multiply(new BigDecimal("0.05")).max(new BigDecimal("150"));
        width = width.add(new BigDecimal("2")); height = height.add(new BigDecimal("2"));
        BigDecimal footprint = width.multiply(height).multiply(new BigDecimal("0.85")).max(BigDecimal.ONE);
        BigDecimal packedDepth = volume.divide(footprint, 2, RoundingMode.CEILING).add(new BigDecimal("2"));
        depth = packedDepth.max(depth.add(new BigDecimal("2"))).max(new BigDecimal("5"));
        return new PackageEstimate(weight.add(packagingWeight).divide(new BigDecimal("1000"), 2, RoundingMode.CEILING), width, height, depth);
    }

    private String buildXml(String account, int operation, String costCenter, StoreReturn storeReturn,
                            BookstoreShippingSettings destination, PackageEstimate pack, Map<String, Object> settings) {
        var order = storeReturn.getOrder();
        String[] clientName = splitName(order.getCustomerName());
        String[] bookstoreName = splitName(first(destination.getOriginName(), "Librería"));
        int timeSlot = intSetting(settings, "idFranjaHorariaDevolucion", intSetting(settings, "idFranjaHoraria", 1));
        String remito = truncate("DEV-" + order.getOrderNumber() + "-" + storeReturn.getId(), 30);
        return "<?xml version=\"1.0\" encoding=\"iso-8859-1\" standalone=\"yes\"?>" +
                "<ROWS><cabecera ver=\"2.0\" nrocuenta=\"" + e(account) + "\" origen=\"API\"/>" +
                "<origenes><origen calle=\"" + e(truncate(order.getShippingDestinationStreet(), 30)) + "\" nro=\"" + e(truncate(order.getShippingDestinationStreetNumber(), 5)) + "\"" +
                " piso=\"" + e(truncate(order.getShippingDestinationFloorApartment(), 2)) + "\" depto=\"\" cp=\"" + e(numericPostal(order.getShippingDestinationPostalCode())) + "\"" +
                " localidad=\"" + e(truncate(order.getShippingDestinationCity(), 30)) + "\" provincia=\"" + e(truncate(order.getShippingDestinationState(), 30)) + "\"" +
                " contacto=\"" + e(truncate(order.getCustomerName(), 30)) + "\" email=\"" + e(truncate(order.getCustomerEmail(), 100)) + "\"" +
                " solicitante=\"\" observaciones=\"Devolucion Anaquel\" centrocosto=\"" + e(costCenter) + "\" idfranjahoraria=\"" + timeSlot + "\"" +
                " idcentroimposicionorigen=\"0\" fecha=\"" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "\">" +
                "<envios><envio idoperativa=\"" + operation + "\" nroremito=\"" + e(remito) + "\">" +
                "<destinatario apellido=\"" + e(truncate(bookstoreName[1], 30)) + "\" nombre=\"" + e(truncate(bookstoreName[0], 30)) + "\"" +
                " calle=\"" + e(truncate(destination.getOriginStreet(), 30)) + "\" nro=\"" + e(truncate(destination.getOriginStreetNumber(), 5)) + "\"" +
                " piso=\"" + e(truncate(destination.getOriginFloorApartment(), 6)) + "\" depto=\"\" localidad=\"" + e(truncate(destination.getOriginCity(), 30)) + "\"" +
                " provincia=\"" + e(truncate(destination.getOriginState(), 30)) + "\" cp=\"" + e(numericPostal(destination.getOriginPostalCode())) + "\"" +
                " telefono=\"" + e(truncate(destination.getOriginPhone(), 30)) + "\" email=\"" + e(truncate(destination.getOriginEmail(), 100)) + "\" idci=\"0\"" +
                " celular=\"" + e(truncate(order.getCustomerPhone(), 15)) + "\" observaciones=\"Devolucion " + e(truncate(order.getOrderNumber(), 70)) + "\"/>" +
                "<paquetes><paquete alto=\"" + decimal(pack.height()) + "\" ancho=\"" + decimal(pack.width()) + "\" largo=\"" + decimal(pack.depth()) + "\"" +
                " peso=\"" + decimal(pack.weightKg()) + "\" valor=\"0\" cant=\"1\"/></paquetes>" +
                "</envio></envios></origen></origenes></ROWS>";
    }

    private BigDecimal positive(BigDecimal value, BigDecimal fallback) { return value != null && value.signum() > 0 ? value : fallback; }
    private int intSetting(Map<String, Object> settings, String key, int fallback) { try { return Integer.parseInt(Objects.toString(settings.get(key), String.valueOf(fallback))); } catch (Exception ex) { return fallback; } }
    private String numericPostal(String value) { if (value == null) return ""; String digits = value.replaceAll("\\D", ""); return digits.length() > 4 ? digits.substring(digits.length() - 4) : digits; }
    private String decimal(BigDecimal value) { return value.setScale(2, RoundingMode.CEILING).toPlainString(); }
    private String e(String value) { return OcaXmlSupport.xmlEscape(Objects.toString(value, "")); }
    private String truncate(String value, int max) { String v = Objects.toString(value, ""); return v.length() <= max ? v : v.substring(0, max); }
    private String[] splitName(String value) { String clean = Objects.toString(value, "").trim(); if (clean.isEmpty()) return new String[]{"Cliente", ""}; int index = clean.lastIndexOf(' '); return index < 0 ? new String[]{clean, ""} : new String[]{clean.substring(0, index), clean.substring(index + 1)}; }
    private String text(Object value) { return value == null ? null : String.valueOf(value).trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String first(String... values) { for (String value : values) if (!blank(value)) return value.trim(); return null; }
    private record PackageEstimate(BigDecimal weightKg, BigDecimal width, BigDecimal height, BigDecimal depth) {}
}
