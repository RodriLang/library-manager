package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto;

import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationIssueType;

import java.math.BigDecimal;

public record TiendanubeReconciliationIssue(
        Long inventoryId,
        Long linkId,
        Long productId,
        Long variantId,
        TiendanubeReconciliationIssueType issueType,
        Integer localStock,
        Integer remoteStock,
        BigDecimal localPrice,
        BigDecimal remotePrice,
        String message
) {
}
