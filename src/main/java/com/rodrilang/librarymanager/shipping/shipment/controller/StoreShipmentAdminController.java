package com.rodrilang.librarymanager.shipping.shipment.controller;

import com.rodrilang.librarymanager.shipping.shipment.dto.CreateShipmentTrackingEventRequest;
import com.rodrilang.librarymanager.shipping.shipment.dto.CreateStoreShipmentRequest;
import com.rodrilang.librarymanager.shipping.shipment.dto.StoreShipmentResponse;
import com.rodrilang.librarymanager.shipping.shipment.dto.UpdateShipmentMetadataRequest;
import com.rodrilang.librarymanager.shipping.shipment.service.StoreShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/store/orders/{orderId}/shipments")
@RequiredArgsConstructor
public class StoreShipmentAdminController {
    private final StoreShipmentService service;

    @GetMapping
    public List<StoreShipmentResponse> list(@PathVariable Long orderId) { return service.list(orderId); }

    @PostMapping
    public StoreShipmentResponse createDraft(@PathVariable Long orderId, @RequestBody(required = false) CreateStoreShipmentRequest request) { return service.createDraft(orderId, request); }

    @PatchMapping("/{shipmentId}")
    public StoreShipmentResponse updateMetadata(@PathVariable Long orderId, @PathVariable Long shipmentId,
                                                @RequestBody UpdateShipmentMetadataRequest request) {
        return service.updateMetadata(orderId, shipmentId, request);
    }

    @PostMapping("/{shipmentId}/tracking/refresh")
    public StoreShipmentResponse refreshTracking(@PathVariable Long orderId, @PathVariable Long shipmentId) {
        return service.refreshTracking(orderId, shipmentId);
    }

    @PostMapping("/{shipmentId}/tracking/events")
    public StoreShipmentResponse addTrackingEvent(@PathVariable Long orderId, @PathVariable Long shipmentId,
                                                  @RequestBody CreateShipmentTrackingEventRequest request) {
        return service.addTrackingEvent(orderId, shipmentId, request);
    }

    @PostMapping("/{shipmentId}/dispatch")
    public StoreShipmentResponse dispatch(@PathVariable Long orderId, @PathVariable Long shipmentId) { return service.markDispatched(orderId, shipmentId); }

    @PostMapping("/{shipmentId}/deliver")
    public StoreShipmentResponse deliver(@PathVariable Long orderId, @PathVariable Long shipmentId) { return service.markDelivered(orderId, shipmentId); }

    @PostMapping("/{shipmentId}/cancel")
    public StoreShipmentResponse cancel(@PathVariable Long orderId, @PathVariable Long shipmentId) { return service.cancel(orderId, shipmentId); }
}
