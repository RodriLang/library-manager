package com.rodrilang.librarymanager.shipping.shipment.dto;

import com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus;

import java.time.Instant;

public record CreateShipmentTrackingEventRequest(
        ShipmentStatus status,
        String providerStatus,
        String description,
        String location,
        Instant eventAt
) {}
