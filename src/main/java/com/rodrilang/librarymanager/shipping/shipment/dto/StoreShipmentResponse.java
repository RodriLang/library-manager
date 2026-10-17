package com.rodrilang.librarymanager.shipping.shipment.dto;

import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StoreShipmentResponse(
        Long id,
        Integer packageSequence,
        ShippingProviderType provider,
        String serviceCode,
        String serviceName,
        ShipmentStatus status,
        String providerShipmentId,
        String trackingNumber,
        String trackingUrl,
        String labelUrl,
        String providerStatus,
        String providerStatusDetail,
        Integer weightGrams,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal depthCm,
        BigDecimal declaredValue,
        String notes,
        Instant createdAt,
        Instant createdAtProvider,
        Instant dispatchedAt,
        Instant deliveredAt,
        Instant cancelledAt,
        List<StoreShipmentItemResponse> items,
        List<StoreShipmentTrackingEventResponse> events
) {}
