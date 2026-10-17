package com.rodrilang.librarymanager.shipping.correoargentino;

import com.fasterxml.jackson.databind.JsonNode;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.shipping.model.BookstoreShippingSettings;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.repository.BookstoreShippingSettingsRepository;
import com.rodrilang.librarymanager.shipping.service.BookstoreShippingConfigurationService;
import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipment;
import com.rodrilang.librarymanager.shipping.shipment.provider.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
public class CorreoArgentinoShipmentClient implements ShipmentCreationClient, ShipmentCancellationClient, ShipmentLabelClient {
    private final CorreoArgentinoClientSupport support;
    private final CorreoArgentinoSettingsReader settingsReader;
    private final BookstoreShippingConfigurationService configurationService;
    private final BookstoreShippingSettingsRepository settingsRepository;

    @Override public ShippingProviderType provider() { return ShippingProviderType.CORREO_ARGENTINO; }

    @Override
    public ShipmentCreationResult create(StoreShipment shipment) {
        Long bookstoreId = shipment.getBookstore().getId();
        Map<String,String> credentials = configurationService.requireCredentials(bookstoreId, provider());
        Map<String,Object> settings = settingsReader.settings(bookstoreId);
        if (!support.hasPaqArCredentials(credentials)) {
            throw new BusinessException("Para generar envíos con Correo Argentino configurá agreement y API-Key de PAQ.AR 2.0.");
        }
        BookstoreShippingSettings origin = settingsRepository.findByBookstoreId(bookstoreId)
                .orElseThrow(() -> new BusinessException("Configurá el origen de los envíos."));
        String originProvince = requireProvince(origin.getOriginState(), "origen");
        String destinationProvince = requireProvince(shipment.getOrder().getShippingDestinationState(), "destino");
        Map<String,Object> body = new LinkedHashMap<>();
        String sellerId = support.sellerId(credentials);
        body.put("sellerId", sellerId == null ? support.agreement(credentials) : sellerId);
        body.put("trackingNumber", "");
        Map<String,Object> order = new LinkedHashMap<>();
        order.put("senderData", sender(origin.getOriginName(), origin.getOriginEmail(), origin.getOriginPhone(),
                address(origin.getOriginStreet(), origin.getOriginStreetNumber(), origin.getOriginCity(), originProvince, origin.getOriginPostalCode(), origin.getOriginFloorApartment())));
        order.put("shippingData", recipient(shipment.getOrder().getCustomerName(), shipment.getOrder().getCustomerEmail(), shipment.getOrder().getCustomerPhone(),
                address(shipment.getOrder().getShippingDestinationStreet(), shipment.getOrder().getShippingDestinationStreetNumber(),
                        shipment.getOrder().getShippingDestinationCity(), destinationProvince, shipment.getOrder().getShippingDestinationPostalCode(), shipment.getOrder().getShippingDestinationFloorApartment())));
        Map<String,Object> parcel = new LinkedHashMap<>();
        parcel.put("dimensions", Map.of(
                "height", integer(shipment.getHeightCm()), "width", integer(shipment.getWidthCm()), "depth", integer(shipment.getDepthCm())));
        parcel.put("productWeight", String.valueOf(Math.max(1, shipment.getWeightGrams() == null ? 1 : shipment.getWeightGrams())));
        parcel.put("productCategory", "LIBROS");
        parcel.put("declaredValue", shipment.getDeclaredValue() == null ? "0" : shipment.getDeclaredValue().setScale(0, RoundingMode.HALF_UP).toPlainString());
        order.put("parcels", List.of(parcel));
        boolean pickup = shipment.getOrder().getShippingDeliveryType() == com.rodrilang.librarymanager.shipping.model.ShippingDeliveryType.BRANCH
                || shipment.getOrder().getShippingDeliveryType() == com.rodrilang.librarymanager.shipping.model.ShippingDeliveryType.PICKUP_POINT;
        order.put("deliveryType", pickup ? "agency" : "homeDelivery");
        order.put("agencyId", pickup ? blankToEmpty(shipment.getOrder().getShippingPickupPointExternalId()) : "");
        order.put("saleDate", OffsetDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        order.put("shipmentClientId", shipment.getOrder().getOrderNumber() + "-" + shipment.getPackageSequence());
        order.put("serviceType", support.serviceType(credentials, settings));
        body.put("order", order);

        try {
            JsonNode response = support.client(support.paqArBaseUrl(settings)).post().uri("/orders")
                    .headers(h -> support.addPaqArHeaders(h, credentials))
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            String tracking = response == null ? null : response.path("trackingNumber").asText(null);
            if (tracking == null || tracking.isBlank()) throw new BusinessException("Correo Argentino creó la orden sin informar trackingNumber.");
            String providerId = response.path("idSeller").asText(null);
            return new ShipmentCreationResult(providerId == null || providerId.isBlank() ? tracking : providerId, tracking, null, "PRE", "Preimposición creada en Correo Argentino.");
        } catch (RestClientResponseException ex) {
            throw new BusinessException("Correo Argentino no pudo generar el envío (HTTP " + ex.getStatusCode().value() + ").");
        }
    }

    @Override
    public void cancel(StoreShipment shipment) {
        if (shipment.getTrackingNumber() == null || shipment.getTrackingNumber().isBlank()) throw new BusinessException("El envío todavía no tiene tracking de Correo Argentino.");
        Long bookstoreId = shipment.getBookstore().getId();
        Map<String,String> credentials = configurationService.requireCredentials(bookstoreId, provider());
        Map<String,Object> settings = settingsReader.settings(bookstoreId);
        try {
            support.client(support.paqArBaseUrl(settings)).patch().uri("/orders/{tracking}/cancel", shipment.getTrackingNumber())
                    .headers(h -> support.addPaqArHeaders(h, credentials)).retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new BusinessException("Correo Argentino no pudo cancelar el envío (HTTP " + ex.getStatusCode().value() + ").");
        }
    }

    @Override
    public ShipmentLabelResult label(StoreShipment shipment) {
        if (shipment.getTrackingNumber() == null || shipment.getTrackingNumber().isBlank()) throw new BusinessException("El envío todavía no tiene tracking de Correo Argentino.");
        Long bookstoreId = shipment.getBookstore().getId();
        Map<String,String> credentials = configurationService.requireCredentials(bookstoreId, provider());
        Map<String,Object> settings = settingsReader.settings(bookstoreId);
        String format = Objects.toString(settings.getOrDefault("labelFormat", "10x15"));
        String sellerId = support.sellerId(credentials);
        if (sellerId == null) sellerId = "";
        List<Map<String,String>> body = List.of(Map.of("sellerId", sellerId, "trackingNumber", shipment.getTrackingNumber()));
        try {
            JsonNode response = support.client(support.paqArBaseUrl(settings)).post()
                    .uri(uri -> uri.path("/labels").queryParam("labelFormat", format).build())
                    .headers(h -> support.addPaqArHeaders(h, credentials)).contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class);
            JsonNode first = response != null && response.isArray() && !response.isEmpty() ? response.get(0) : null;
            String base64 = first == null ? null : first.path("fileBase64").asText(null);
            if (base64 == null || base64.isBlank()) throw new BusinessException("Correo Argentino no devolvió el rótulo del envío.");
            String fileName = first.path("fileName").asText(first.path("filename").asText("correo-argentino-" + shipment.getTrackingNumber() + ".pdf"));
            return new ShipmentLabelResult(Base64.getDecoder().decode(base64), fileName, "application/pdf");
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Correo Argentino devolvió un rótulo inválido.");
        } catch (RestClientResponseException ex) {
            throw new BusinessException("Correo Argentino no pudo generar el rótulo (HTTP " + ex.getStatusCode().value() + ").");
        }
    }

    private Map<String,Object> sender(String name, String email, String phone, Map<String,Object> address) {
        Map<String,Object> p = new LinkedHashMap<>();
        p.put("id", ""); p.put("businessName", blankToEmpty(name));
        p.put("areaCodePhone", ""); p.put("phoneNumber", blankToEmpty(phone));
        p.put("areaCodeCellphone", ""); p.put("cellphoneNumber", blankToEmpty(phone));
        p.put("email", blankToEmpty(email)); p.put("observation", ""); p.put("address", address); return p;
    }
    private Map<String,Object> recipient(String name, String email, String phone, Map<String,Object> address) {
        Map<String,Object> p = new LinkedHashMap<>();
        p.put("name", blankToEmpty(name));
        p.put("areaCodePhone", ""); p.put("phoneNumber", blankToEmpty(phone));
        p.put("areaCodeCellphone", ""); p.put("cellphoneNumber", blankToEmpty(phone));
        p.put("email", blankToEmpty(email)); p.put("observation", ""); p.put("address", address); return p;
    }
    private Map<String,Object> address(String street, String number, String city, String state, String postal, String floorApartment) {
        Map<String,Object> a = new LinkedHashMap<>();
        a.put("streetName", blankToEmpty(street)); a.put("streetNumber", blankToEmpty(number)); a.put("cityName", blankToEmpty(city));
        a.put("floor", truncate(blankToEmpty(floorApartment), 20)); a.put("department", ""); a.put("state", state); a.put("zipCode", blankToEmpty(postal)); return a;
    }
    private String requireProvince(String value, String label) { String c=CorreoArgentinoProvinceCodes.code(value); if(c==null) throw new BusinessException("No se pudo mapear la provincia de " + label + " para Correo Argentino: " + value); return c; }
    private int integer(java.math.BigDecimal value) { return Math.max(1, value == null ? 1 : value.setScale(0, RoundingMode.CEILING).intValue()); }
    private String blankToEmpty(String v) { return v == null ? "" : v.trim(); }
    private String truncate(String v, int max) { return v.length() <= max ? v : v.substring(0,max); }
}
