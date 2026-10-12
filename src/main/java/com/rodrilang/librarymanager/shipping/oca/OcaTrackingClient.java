package com.rodrilang.librarymanager.shipping.oca;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.service.BookstoreShippingConfigurationService;
import com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus;
import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipment;
import com.rodrilang.librarymanager.shipping.shipment.tracking.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
public class OcaTrackingClient implements ShipmentTrackingClient {
    private final OcaClientSupport support;
    private final OcaSettingsReader settingsReader;
    private final BookstoreShippingConfigurationService configurationService;

    @Override public ShippingProviderType provider() { return ShippingProviderType.OCA; }

    @Override
    public ProviderTrackingSnapshot track(StoreShipment shipment) {
        if (shipment.getTrackingNumber() == null || shipment.getTrackingNumber().isBlank()) throw new BusinessException("El envío no tiene número de OCA.");
        Long bookstoreId = shipment.getBookstore().getId();
        Map<String,String> credentials = configurationService.requireCredentials(bookstoreId, provider());
        Map<String,Object> settings = settingsReader.settings(bookstoreId);
        String cuit = support.require(credentials, "cuit", "CUIT");
        String xml = support.post(settings, "Tracking_Pieza", Map.of(
                "Pieza", shipment.getTrackingNumber(),
                "NroDocumentoCliente", shipment.getOrder().getOrderNumber(),
                "CUIT", cuit
        ));
        Document doc = OcaXmlSupport.parse(xml);
        List<ProviderTrackingEvent> events = new ArrayList<>();
        int idx = 0;
        for (Element table : OcaXmlSupport.elements(doc, "Table")) {
            String state = OcaXmlSupport.text(table, "Desdcripcion_Estado", "Descripcion_Estado", "Estado");
            String reason = OcaXmlSupport.text(table, "Descripcion_Motivo", "Motivo");
            String branch = OcaXmlSupport.text(table, "SUC", "Sucursal");
            String date = OcaXmlSupport.text(table, "fecha", "Fecha");
            Instant at = parseDate(date);
            String description = join(state, reason);
            ShipmentStatus normalized = normalize(description);
            events.add(new ProviderTrackingEvent(shipment.getTrackingNumber()+"|"+(date==null?idx:date)+"|"+(idx++), state, normalized,
                    description, branch, at, table.getTextContent()));
        }
        ProviderTrackingEvent last = events.stream().max(Comparator.comparing(e -> e.eventAt()==null?Instant.EPOCH:e.eventAt())).orElse(null);
        return new ProviderTrackingSnapshot(last==null?null:last.providerStatus(), last==null?null:last.description(), support.trackingUrl(shipment.getTrackingNumber()), List.copyOf(events));
    }

    private ShipmentStatus normalize(String value) {
        String s = value == null ? "" : value.toUpperCase(Locale.ROOT);
        if (s.contains("ENTREGAD")) return ShipmentStatus.DELIVERED;
        if (s.contains("VISITA") || s.contains("DISTRIB") || s.contains("REPART")) return ShipmentStatus.OUT_FOR_DELIVERY;
        if (s.contains("ANUL") || s.contains("CANCEL")) return ShipmentStatus.CANCELLED;
        if (s.contains("DEVOL") || s.contains("RECHAZ") || s.contains("SINIEST") || s.contains("EXCEPC") || s.contains("IMPOSIBLE")) return ShipmentStatus.EXCEPTION;
        if (s.contains("ADMISION") || s.contains("INGRES") || s.contains("GENERAD") || s.contains("ORDEN")) return ShipmentStatus.READY_TO_SHIP;
        return ShipmentStatus.IN_TRANSIT;
    }
    private String join(String a,String b){ if(a==null)return b; if(b==null)return a; return a+" · "+b; }
    private Instant parseDate(String value) {
        if(value==null||value.isBlank()) return null;
        List<DateTimeFormatter> formats=List.of(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
        for(var f:formats) try{return LocalDateTime.parse(value.trim(),f).atZone(ZoneId.of("America/Argentina/Buenos_Aires")).toInstant();}catch(Exception ignored){}
        try{return OffsetDateTime.parse(value).toInstant();}catch(Exception ignored){}
        return null;
    }
}
