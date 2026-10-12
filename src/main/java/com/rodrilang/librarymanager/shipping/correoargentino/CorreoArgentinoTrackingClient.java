package com.rodrilang.librarymanager.shipping.correoargentino;

import com.fasterxml.jackson.databind.JsonNode;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.service.BookstoreShippingConfigurationService;
import com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus;
import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipment;
import com.rodrilang.librarymanager.shipping.shipment.tracking.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
public class CorreoArgentinoTrackingClient implements ShipmentTrackingClient {
    private final CorreoArgentinoClientSupport support;
    private final CorreoArgentinoSettingsReader settingsReader;
    private final BookstoreShippingConfigurationService configurationService;
    @Override public ShippingProviderType provider() { return ShippingProviderType.CORREO_ARGENTINO; }

    @Override
    public ProviderTrackingSnapshot track(StoreShipment shipment) {
        if (shipment.getTrackingNumber() == null || shipment.getTrackingNumber().isBlank()) throw new BusinessException("El envío no tiene trackingNumber de Correo Argentino.");
        Long bookstoreId = shipment.getBookstore().getId();
        Map<String,String> credentials = configurationService.requireCredentials(bookstoreId, provider());
        Map<String,Object> settings = settingsReader.settings(bookstoreId);
        String extClient = Objects.toString(settings.getOrDefault("extClient", ""));
        try {
            JsonNode response = support.client(support.paqArBaseUrl(settings)).method(HttpMethod.GET)
                    .uri(uri -> { var b=uri.path("/tracking"); if(!extClient.isBlank()) b.queryParam("extClient", extClient); return b.build(); })
                    .headers(h -> support.addPaqArHeaders(h, credentials)).contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(Map.of("trackingNumber", shipment.getTrackingNumber())))
                    .retrieve().body(JsonNode.class);
            JsonNode item = response != null && response.isArray() && !response.isEmpty() ? response.get(0) : response;
            List<ProviderTrackingEvent> events = new ArrayList<>();
            if (item != null && item.path("event").isArray()) {
                int idx=0;
                for (JsonNode e : item.path("event")) {
                    String statusId=e.path("statusId").asText(""); String status=e.path("status").asText(""); String facility=e.path("facility").asText(null);
                    Instant at=parseDate(e.path("date").asText(null)); ShipmentStatus normalized=normalize(statusId,status);
                    String eventId=shipment.getTrackingNumber()+"|"+statusId+"|"+(at==null?"":at.toString())+"|"+(idx++);
                    events.add(new ProviderTrackingEvent(eventId,status,normalized,status,facility,at,e.toString()));
                }
            }
            ProviderTrackingEvent last = events.stream().max(Comparator.comparing(e -> e.eventAt()==null?Instant.EPOCH:e.eventAt())).orElse(null);
            return new ProviderTrackingSnapshot(last==null?null:last.providerStatus(), last==null?null:last.description(), null, List.copyOf(events));
        } catch (RestClientResponseException ex) {
            throw new BusinessException("Correo Argentino no pudo consultar el tracking (HTTP " + ex.getStatusCode().value() + ").");
        }
    }

    private ShipmentStatus normalize(String id, String text) {
        String s=(id+" "+text).toUpperCase(Locale.ROOT);
        if(s.contains("ENTREG") || s.contains("DELIVER")) return ShipmentStatus.DELIVERED;
        if(s.contains("DISTRIB") || s.contains("REPART") || s.contains("VISITA")) return ShipmentStatus.OUT_FOR_DELIVERY;
        if(s.contains("CANCEL") || s.contains("CADUCO") || s.contains("CAU")) return ShipmentStatus.CANCELLED;
        if(s.contains("RECHAZ") || s.contains("DEVOL") || s.contains("ERROR") || s.contains("INCONVENIENT")) return ShipmentStatus.EXCEPTION;
        if(s.contains("PREIMPOS") || s.equals("PRE")) return ShipmentStatus.READY_TO_SHIP;
        return ShipmentStatus.IN_TRANSIT;
    }
    private Instant parseDate(String value) {
        if(value==null||value.isBlank()) return null;
        try { return OffsetDateTime.parse(value).toInstant(); } catch(Exception ignored) {}
        try { return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")).atZone(ZoneId.of("America/Argentina/Buenos_Aires")).toInstant(); } catch(Exception ignored) {}
        return null;
    }
}
