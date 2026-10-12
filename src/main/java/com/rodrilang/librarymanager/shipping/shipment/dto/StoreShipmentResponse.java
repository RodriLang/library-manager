package com.rodrilang.librarymanager.shipping.shipment.dto;

import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record StoreShipmentResponse(
        Long id,
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
        Instant createdAt,
        Instant createdAtProvider,
        Instant dispatchedAt,
        Instant deliveredAt,
        Instant cancelledAt
) {}
