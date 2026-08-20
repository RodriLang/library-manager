package com.rodrilang.librarymanager.purchasing.order.dto.response;

import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PurchaseOrderResponse(

        Long id,
        String orderNumber,

        Long providerId,
        String providerName,

        PurchaseOrderStatus status,

        Integer itemCount,
        Integer totalUnits,
        BigDecimal estimatedTotal,

        Instant createdAt,
        Instant sentAt

) {
}