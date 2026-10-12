package com.rodrilang.librarymanager.shipping.shipment.controller;

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
    public List<StoreShipmentResponse> list(@PathVariable Long orderId) {
        return service.list(orderId);
    }

    @PostMapping
    public StoreShipmentResponse createDraft(@PathVariable Long orderId) {
        return service.createDraft(orderId);
    }

    @PatchMapping("/{shipmentId}")
    public StoreShipmentResponse updateMetadata(@PathVariable Long orderId,
                                                @PathVariable Long shipmentId,
                                                @RequestBody UpdateShipmentMetadataRequest request) {
        return service.updateMetadata(orderId, shipmentId, request);
    }

    @PostMapping("/{shipmentId}/dispatch")
    public StoreShipmentResponse dispatch(@PathVariable Long orderId, @PathVariable Long shipmentId) {
        return service.markDispatched(orderId, shipmentId);
    }

    @PostMapping("/{shipmentId}/deliver")
    public StoreShipmentResponse deliver(@PathVariable Long orderId, @PathVariable Long shipmentId) {
        return service.markDelivered(orderId, shipmentId);
    }

    @PostMapping("/{shipmentId}/cancel")
    public StoreShipmentResponse cancel(@PathVariable Long orderId, @PathVariable Long shipmentId) {
        return service.cancel(orderId, shipmentId);
    }
}
