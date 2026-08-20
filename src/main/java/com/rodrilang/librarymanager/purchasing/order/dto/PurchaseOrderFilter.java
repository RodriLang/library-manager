package com.rodrilang.librarymanager.purchasing.order.dto;

import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderStatus;

public record PurchaseOrderFilter(
        String query,
        Long providerId,
        PurchaseOrderStatus status
) {
}