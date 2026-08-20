package com.rodrilang.librarymanager.purchasing.order.dto.response;

import java.math.BigDecimal;

public record PreparedPurchaseOrderResponse(
        Long orderId,
        String orderNumber,
        Long providerId,
        String providerName,
        Integer itemCount,
        Integer totalUnits,
        BigDecimal estimatedTotal,
        boolean created
) {
}