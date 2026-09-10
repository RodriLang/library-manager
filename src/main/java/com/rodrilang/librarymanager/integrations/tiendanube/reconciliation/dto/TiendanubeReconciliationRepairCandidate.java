package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto;

import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationIssueType;

import java.time.Instant;

public record TiendanubeReconciliationRepairCandidate(
        Long itemId,
        Long runId,
        Long bookstoreId,
        Long tiendanubeStoreId,
        Long storeId,
        Long inventoryId,
        Long linkId,
        Long productId,
        Long variantId,
        TiendanubeReconciliationIssueType issueType,
        Instant repairRequestedAt
) {
}
