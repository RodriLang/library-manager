package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto;

import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationIssueType;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationRepairSource;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationRepairStatus;

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
        TiendanubeReconciliationRepairStatus repairStatus,
        TiendanubeReconciliationRepairSource repairSource,
        Long repairJobId,
        TiendanubeJobStatus repairJobStatus,
        Integer repairAttemptCount,
        Integer repairMaxAttempts,
        String repairErrorType,
        String repairErrorMessage,
        Instant repairRequestedAt,
        Instant repairCompletedAt,
        Instant createdAt
) {
}
