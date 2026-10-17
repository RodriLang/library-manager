package com.rodrilang.librarymanager.shipping.shipment.dto;

import com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus;

import java.time.Instant;

public record StoreShipmentTrackingEventResponse(
        Long id,
        String providerStatus,
        ShipmentStatus status,
        String description,
        String location,
        Instant eventAt
) {}
