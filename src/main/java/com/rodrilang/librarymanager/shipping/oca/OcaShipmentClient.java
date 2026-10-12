package com.rodrilang.librarymanager.shipping.oca;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.shipping.model.BookstoreShippingSettings;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.repository.BookstoreShippingSettingsRepository;
import com.rodrilang.librarymanager.shipping.service.BookstoreShippingConfigurationService;
import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipment;
import com.rodrilang.librarymanager.shipping.shipment.provider.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
public class OcaShipmentClient implements ShipmentCreationClient, ShipmentCancellationClient, ShipmentLabelClient {
    private final OcaClientSupport support;
    private final OcaSettingsReader settingsReader;
    private final BookstoreShippingConfigurationService configurationService;
    private final BookstoreShippingSettingsRepository settingsRepository;

    @Override public ShippingProviderType provider() { return ShippingProviderType.OCA; }

    @Override
    public ShipmentCreationResult create(StoreShipment shipment) {
        Long bookstoreId = shipment.getBookstore().getId();
        Map<String,String> credentials = configurationService.requireCredentials(bookstoreId, provider());
        Map<String,Object> settings = settingsReader.settings(bookstoreId);
        String user = support.require(credentials, "usuario", "usuario e-Pak");
        String password = support.require(credentials, "password", "contraseña e-Pak");
        String account = support.require(credentials, "nroCuenta", "número de cuenta");
        int operativa = support.requireInt(credentials, settings, "operativa", "operativa");
        String costCenter = first(credentials.get("centroCosto"), Objects.toString(settings.get("centroCosto"), null));
        if (costCenter == null || costCenter.isBlank()) throw new BusinessException("Configurá el centro de costo de OCA (centroCosto).");

        BookstoreShippingSettings origin = settingsRepository.findByBookstoreId(bookstoreId)
                .orElseThrow(() -> new BusinessException("Configurá el origen de los envíos."));
        String customer = shipment.getOrder().getCustomerName();
        String[] name = splitName(customer);
        String xmlData = buildXml(account, operativa, costCenter, origin, shipment, name[0], name[1], settings);
        boolean confirm = Boolean.parseBoolean(Objects.toString(settings.getOrDefault("confirmarRetiro", "true")));
        String response = support.post(settings, "IngresoORMultiplesRetiros_v2", Map.of(
                "usr", user,
                "psw", password,
                "XML_Datos", xmlData,
                "ConfirmarRetiro", String.valueOf(confirm).toLowerCase(Locale.ROOT),
                "ArchivoCliente", "",
                "ArchivoProceso", ""
        ));
        Document doc = OcaXmlSupport.parse(response);
        List<Element> details = OcaXmlSupport.elements(doc, "DetalleIngresos");
        if (details.isEmpty()) throw new BusinessException("OCA no devolvió el detalle de la orden creada.");
        Element detail = details.get(0);
        String orderId = OcaXmlSupport.text(detail, "OrdenRetiro");
        String tracking = OcaXmlSupport.text(detail, "NumeroEnvio");
        String status = OcaXmlSupport.text(detail, "Estado");
        if (tracking == null || tracking.isBlank()) throw new BusinessException("OCA no devolvió el número de envío. Estado: " + Objects.toString(status, "sin detalle"));
        return new ShipmentCreationResult(orderId, tracking, support.trackingUrl(tracking), status, "Orden e-Pak creada en OCA.");
    }

    @Override
    public void cancel(StoreShipment shipment) {
        if (shipment.getProviderShipmentId() == null || shipment.getProviderShipmentId().isBlank()) throw new BusinessException("El envío no tiene Orden de Retiro/Admisión de OCA.");
        Long bookstoreId = shipment.getBookstore().getId();
        Map<String,String> credentials = configurationService.requireCredentials(bookstoreId, provider());
        Map<String,Object> settings = settingsReader.settings(bookstoreId);
        String response = support.post(settings, "AnularOrdenGenerada", Map.of(
                "usr", support.require(credentials, "usuario", "usuario e-Pak"),
                "psw", support.require(credentials, "password", "contraseña e-Pak"),
                "idOrdenRetiro", shipment.getProviderShipmentId()
        ));
        Document doc = OcaXmlSupport.parse(response);
        String code = OcaXmlSupport.first(doc, "Codigo", "Resultado", "AnularOrdenGeneradaResult");
        if (code != null && !(code.contains("100") || code.toLowerCase(Locale.ROOT).contains("exit"))) {
            throw new BusinessException("OCA no pudo anular la orden: " + code);
        }
    }

    @Override
    public ShipmentLabelResult label(StoreShipment shipment) {
        if ((shipment.getTrackingNumber() == null || shipment.getTrackingNumber().isBlank()) &&
                (shipment.getProviderShipmentId() == null || shipment.getProviderShipmentId().isBlank())) {
            throw new BusinessException("El envío todavía no tiene número de OCA.");
        }
        Map<String,Object> settings = settingsReader.settings(shipment.getBookstore().getId());
        String operation = "10x15".equalsIgnoreCase(Objects.toString(settings.getOrDefault("labelFormat", "10x15")))
                ? "GetPdfDeEtiquetasPorOrdenOrNumeroEnvioParaEtiquetadora"
                : "GetPdfDeEtiquetasPorOrdenOrNumeroEnvio";
        String response = support.post(settings, operation, Map.of(
                "idOrdenRetiro", Objects.toString(shipment.getProviderShipmentId(), ""),
                "nroEnvio", Objects.toString(shipment.getTrackingNumber(), ""),
                "logisticaInversa", "false"
        ));
        Document doc = OcaXmlSupport.parse(response);
        String base64 = doc.getDocumentElement().getTextContent();
        if (base64 == null || base64.isBlank()) throw new BusinessException("OCA no devolvió el PDF de la etiqueta.");
        try {
            byte[] pdf = Base64.getMimeDecoder().decode(base64.trim());
            return new ShipmentLabelResult(pdf, "oca-" + Objects.toString(shipment.getTrackingNumber(), shipment.getId().toString()) + ".pdf", "application/pdf");
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("OCA devolvió una etiqueta en un formato no reconocido.");
        }
    }

    private String buildXml(String account, int operativa, String costCenter, BookstoreShippingSettings origin,
                            StoreShipment shipment, String firstName, String lastName, Map<String,Object> settings) {
        String floor = Objects.toString(origin.getOriginFloorApartment(), "");
        String destFloor = Objects.toString(shipment.getOrder().getShippingDestinationFloorApartment(), "");
        int timeSlot = intSetting(settings, "idFranjaHoraria", 1);
        int originCi = intSetting(settings, "idCentroImposicionOrigen", 0);
        boolean insurance = Boolean.parseBoolean(Objects.toString(settings.getOrDefault("seguro", "false")));
        BigDecimal declared = insurance && shipment.getDeclaredValue()!=null ? shipment.getDeclaredValue() : BigDecimal.ZERO;
        BigDecimal kg = BigDecimal.valueOf(Math.max(1, shipment.getWeightGrams()==null?1:shipment.getWeightGrams()))
                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.CEILING);
        String remito = truncate(shipment.getOrder().getOrderNumber()+"-"+shipment.getPackageSequence(), 30);
        return "<?xml version=\"1.0\" encoding=\"iso-8859-1\" standalone=\"yes\"?>"+
                "<ROWS><cabecera ver=\"2.0\" nrocuenta=\""+e(account)+"\" origen=\"API\"/>"+
                "<origenes><origen calle=\""+e(truncate(origin.getOriginStreet(),30))+"\" nro=\""+e(truncate(origin.getOriginStreetNumber(),5))+"\""+
                " piso=\""+e(truncate(floor,2))+"\" depto=\"\" cp=\""+e(numericPostal(origin.getOriginPostalCode()))+"\""+
                " localidad=\""+e(truncate(origin.getOriginCity(),30))+"\" provincia=\""+e(truncate(origin.getOriginState(),30))+"\""+
                " contacto=\""+e(truncate(origin.getOriginContactName(),30))+"\" email=\""+e(truncate(origin.getOriginEmail(),100))+"\""+
                " solicitante=\"\" observaciones=\"Anaquel\" centrocosto=\""+e(costCenter)+"\" idfranjahoraria=\""+timeSlot+"\""+
                " idcentroimposicionorigen=\""+originCi+"\" fecha=\""+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"\">"+
                "<envios><envio idoperativa=\""+operativa+"\" nroremito=\""+e(remito)+"\">"+
                "<destinatario apellido=\""+e(truncate(lastName,30))+"\" nombre=\""+e(truncate(firstName,30))+"\""+
                " calle=\""+e(truncate(shipment.getOrder().getShippingDestinationStreet(),30))+"\" nro=\""+e(truncate(shipment.getOrder().getShippingDestinationStreetNumber(),5))+"\""+
                " piso=\""+e(truncate(destFloor,6))+"\" depto=\"\" localidad=\""+e(truncate(shipment.getOrder().getShippingDestinationCity(),30))+"\""+
                " provincia=\""+e(truncate(shipment.getOrder().getShippingDestinationState(),30))+"\" cp=\""+e(numericPostal(shipment.getOrder().getShippingDestinationPostalCode()))+"\""+
                " telefono=\""+e(truncate(shipment.getOrder().getCustomerPhone(),30))+"\" email=\""+e(truncate(shipment.getOrder().getCustomerEmail(),100))+"\" idci=\"0\""+
                " celular=\""+e(truncate(shipment.getOrder().getCustomerPhone(),15))+"\" observaciones=\"Anaquel pedido "+e(truncate(shipment.getOrder().getOrderNumber(),30))+"\"/>"+
                "<paquetes><paquete alto=\""+num(shipment.getHeightCm())+"\" ancho=\""+num(shipment.getWidthCm())+"\" largo=\""+num(shipment.getDepthCm())+"\""+
                " peso=\""+kg.toPlainString()+"\" valor=\""+declared.setScale(2,RoundingMode.HALF_UP).toPlainString()+"\" cant=\"1\"/></paquetes>"+
                "</envio></envios></origen></origenes></ROWS>";
    }

    private String[] splitName(String full) { String v=full==null?"Cliente":full.trim(); int i=v.lastIndexOf(' '); return i<1?new String[]{v,"."}:new String[]{v.substring(0,i),v.substring(i+1)}; }
    private String numericPostal(String value){String d=value==null?"":value.replaceAll("[^0-9]",""); return d.length()>4?d.substring(0,4):d;}
    private String truncate(String value,int max){String v=value==null?"":value.trim(); return v.length()<=max?v:v.substring(0,max);}
    private String num(BigDecimal value){return (value==null?BigDecimal.ONE:value.max(BigDecimal.ONE)).setScale(2,RoundingMode.CEILING).toPlainString();}
    private String e(String value){return OcaXmlSupport.xmlEscape(value);}
    private int intSetting(Map<String,Object> s,String key,int fallback){try{return Integer.parseInt(Objects.toString(s.get(key),String.valueOf(fallback)));}catch(Exception e){return fallback;}}
    private String first(String a,String b){return a!=null&&!a.isBlank()?a:(b==null||b.isBlank()?null:b);}
}
