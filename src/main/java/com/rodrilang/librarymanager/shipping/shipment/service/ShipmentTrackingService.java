package com.rodrilang.librarymanager.shipping.shipment.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.notification.service.NotificationService;
import com.rodrilang.librarymanager.shipping.shipment.dto.CreateShipmentTrackingEventRequest;
import com.rodrilang.librarymanager.shipping.shipment.dto.StoreShipmentTrackingEventResponse;
import com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus;
import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipment;
import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipmentTrackingEvent;
import com.rodrilang.librarymanager.shipping.shipment.repository.StoreShipmentTrackingEventRepository;
import com.rodrilang.librarymanager.shipping.shipment.repository.StoreShipmentRepository;
import com.rodrilang.librarymanager.shipping.shipment.tracking.ProviderTrackingEvent;
import com.rodrilang.librarymanager.shipping.shipment.tracking.ProviderTrackingSnapshot;
import com.rodrilang.librarymanager.shipping.shipment.tracking.ShipmentTrackingClient;
import com.rodrilang.librarymanager.shipping.shipment.tracking.ShipmentTrackingClientRegistry;
import com.rodrilang.librarymanager.store.order.model.StoreFulfillmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShipmentTrackingService {
    private final StoreShipmentTrackingEventRepository eventRepository;
    private final StoreShipmentRepository shipmentRepository;
    private final ShipmentTrackingClientRegistry clientRegistry;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<StoreShipmentTrackingEventResponse> events(Long shipmentId) {
        return eventRepository.findAllByShipmentIdOrderByEventAtAscIdAsc(shipmentId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public StoreShipmentTrackingEventResponse addManualEvent(StoreShipment shipment, CreateShipmentTrackingEventRequest request) {
        if (request == null || request.status() == null) {
            throw new BusinessException("Elegí un estado para registrar el evento de seguimiento.");
        }
        if (shipment.getStatus() == ShipmentStatus.CANCELLED || shipment.getStatus() == ShipmentStatus.DELIVERED) {
            throw new BusinessException("El envío ya está finalizado y no admite nuevos eventos.");
        }
        StoreShipmentTrackingEvent event = eventRepository.save(StoreShipmentTrackingEvent.builder()
                .shipment(shipment)
                .providerStatus(normalize(request.providerStatus()))
                .normalizedStatus(request.status())
                .description(normalize(request.description()))
                .location(normalize(request.location()))
                .eventAt(request.eventAt() != null ? request.eventAt() : Instant.now())
                .build());
        applyStatus(shipment, request.status(), event.getEventAt(), request.providerStatus(), request.description(), true);
        return toResponse(event);
    }

    @Transactional
    public void recordLocalEvent(StoreShipment shipment, ShipmentStatus status, String description) {
        recordLocalEvent(shipment, status, description, true);
    }

    @Transactional
    public void recordDispatched(StoreShipment shipment) {
        recordLocalEvent(shipment, ShipmentStatus.IN_TRANSIT, "Pedido despachado.", false);
        notificationService.notifyShipmentDispatched(shipment);
    }

    private void recordLocalEvent(StoreShipment shipment, ShipmentStatus status, String description, boolean notify) {
        Instant now = Instant.now();
        eventRepository.save(StoreShipmentTrackingEvent.builder()
                .shipment(shipment)
                .normalizedStatus(status)
                .description(description)
                .eventAt(now)
                .build());
        applyStatus(shipment, status, now, null, description, notify);
    }

    @Transactional
    public void refresh(StoreShipment shipment) {
        ShipmentTrackingClient client = clientRegistry.find(shipment.getProvider())
                .orElseThrow(() -> new BusinessException("El seguimiento automático de " + shipment.getProvider() + " todavía no está disponible."));
        ProviderTrackingSnapshot snapshot = client.track(shipment);
        applySnapshot(shipment, snapshot);
    }

    @Transactional
    public void refreshIfSupported(StoreShipment shipment) {
        clientRegistry.find(shipment.getProvider()).ifPresent(client -> applySnapshot(shipment, client.track(shipment)));
    }

    private void applySnapshot(StoreShipment shipment, ProviderTrackingSnapshot snapshot) {
        if (snapshot == null) return;
        shipment.setProviderStatus(normalize(snapshot.providerStatus()));
        shipment.setProviderStatusDetail(normalize(snapshot.providerStatusDetail()));
        if (normalize(snapshot.trackingUrl()) != null) shipment.setTrackingUrl(normalize(snapshot.trackingUrl()));

        List<ProviderTrackingEvent> providerEvents = snapshot.events() == null ? List.of() : snapshot.events().stream()
                .filter(event -> event != null && event.normalizedStatus() != null)
                .sorted(Comparator.comparing(event -> event.eventAt() == null ? Instant.EPOCH : event.eventAt()))
                .toList();

        for (ProviderTrackingEvent event : providerEvents) {
            String eventId = normalize(event.providerEventId());
            if (eventId != null && eventRepository.findByShipmentIdAndProviderEventId(shipment.getId(), eventId).isPresent()) continue;
            Instant eventAt = event.eventAt() != null ? event.eventAt() : Instant.now();
            eventRepository.save(StoreShipmentTrackingEvent.builder()
                    .shipment(shipment)
                    .providerEventId(eventId)
                    .providerStatus(normalize(event.providerStatus()))
                    .normalizedStatus(event.normalizedStatus())
                    .description(normalize(event.description()))
                    .location(normalize(event.location()))
                    .eventAt(eventAt)
                    .rawPayload(event.rawPayload())
                    .build());
            applyStatus(shipment, event.normalizedStatus(), eventAt, event.providerStatus(), event.description(), true);
        }
    }

    private void applyStatus(StoreShipment shipment, ShipmentStatus newStatus, Instant at, String providerStatus, String detail, boolean notify) {
        ShipmentStatus previous = shipment.getStatus();
        shipment.setStatus(newStatus);
        if (normalize(providerStatus) != null) shipment.setProviderStatus(normalize(providerStatus));
        if (normalize(detail) != null) shipment.setProviderStatusDetail(normalize(detail));

        if ((newStatus == ShipmentStatus.IN_TRANSIT || newStatus == ShipmentStatus.OUT_FOR_DELIVERY) && shipment.getDispatchedAt() == null) {
            shipment.setDispatchedAt(at);
        }
        if (newStatus == ShipmentStatus.DELIVERED) shipment.setDeliveredAt(at);
        if (newStatus == ShipmentStatus.CANCELLED) shipment.setCancelledAt(at);

        recalculateOrderFulfillment(shipment);

        if (notify && previous != newStatus) notificationService.notifyShipmentStatus(shipment, newStatus);
    }

    private void recalculateOrderFulfillment(StoreShipment changed) {
        List<StoreShipment> shipments = shipmentRepository.findAllByOrderIdAndBookstoreIdOrderByCreatedAtDesc(
                changed.getOrder().getId(), changed.getBookstore().getId());
        List<StoreShipment> active = shipments.stream().filter(s -> s.getStatus() != ShipmentStatus.CANCELLED).toList();
        if (active.isEmpty()) {
            if (changed.getOrder().getFulfillmentStatus() != StoreFulfillmentStatus.DELIVERED) changed.getOrder().setFulfillmentStatus(StoreFulfillmentStatus.PREPARING);
            return;
        }
        if (active.stream().allMatch(s -> s.getStatus() == ShipmentStatus.DELIVERED)) {
            changed.getOrder().setFulfillmentStatus(StoreFulfillmentStatus.DELIVERED);
        } else if (active.stream().anyMatch(s -> s.getStatus() == ShipmentStatus.IN_TRANSIT || s.getStatus() == ShipmentStatus.OUT_FOR_DELIVERY || s.getStatus() == ShipmentStatus.DELIVERED)) {
            changed.getOrder().setFulfillmentStatus(StoreFulfillmentStatus.SHIPPED);
        } else {
            changed.getOrder().setFulfillmentStatus(StoreFulfillmentStatus.PREPARING);
        }
    }

    public StoreShipmentTrackingEventResponse toResponse(StoreShipmentTrackingEvent event) {
        return new StoreShipmentTrackingEventResponse(event.getId(), event.getProviderStatus(), event.getNormalizedStatus(),
                event.getDescription(), event.getLocation(), event.getEventAt());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
