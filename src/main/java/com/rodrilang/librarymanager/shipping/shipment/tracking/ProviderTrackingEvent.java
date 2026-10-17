package com.rodrilang.librarymanager.shipping.shipment.tracking;

import com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus;

import java.time.Instant;

public record ProviderTrackingEvent(
        String providerEventId,
        String providerStatus,
        ShipmentStatus normalizedStatus,
        String description,
        String location,
        Instant eventAt,
        String rawPayload
) {}
