package com.rodrilang.librarymanager.shipping.shipment.dto;

import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus;
import java.time.Instant;
import java.util.List;

public record StoreShipmentPublicResponse(
        Integer packageSequence,
        ShippingProviderType provider,
        String serviceName,
        ShipmentStatus status,
        String trackingNumber,
        String trackingUrl,
        String providerStatus,
        String providerStatusDetail,
        Instant dispatchedAt,
        Instant deliveredAt,
        List<StoreShipmentItemResponse> items,
        List<StoreShipmentTrackingEventResponse> events
) {}
