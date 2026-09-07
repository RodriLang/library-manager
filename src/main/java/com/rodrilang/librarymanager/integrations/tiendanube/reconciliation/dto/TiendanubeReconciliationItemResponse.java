package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto;

import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationIssueType;

import java.math.BigDecimal;
import java.time.Instant;

public record TiendanubeReconciliationItemResponse(
        Long id,
        Long inventoryId,
        String title,
        String isbn,
        Long linkId,
        TiendanubeReconciliationIssueType issueType,
        Long productId,
        Long variantId,
        Integer localStock,
        Integer remoteStock,
        BigDecimal localPrice,
        BigDecimal remotePrice,
        String message,
        Instant createdAt
) {
}
