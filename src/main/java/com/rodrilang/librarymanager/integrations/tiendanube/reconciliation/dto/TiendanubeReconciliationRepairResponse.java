package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto;

public record TiendanubeReconciliationRepairResponse(
        Long runId,
        int repairableCount,
        int queuedCount,
        int alreadyRequestedCount,
        int manualReviewCount,
        int failedCount
) {
}
