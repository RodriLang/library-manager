package com.rodrilang.librarymanager.purchasing.order.dto.response;

import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PurchaseOrderDetailResponse(

        Long id,

        String orderNumber,

        Long providerId,
        String providerName,

        PurchaseOrderStatus status,

        String notes,

        Integer itemCount,
        Integer totalUnits,
        Integer receivedUnits,
        Integer pendingUnits,
        BigDecimal estimatedTotal,

        Instant createdAt,
        Instant sentAt,

        List<PurchaseOrderItemResponse> items

) {
}
