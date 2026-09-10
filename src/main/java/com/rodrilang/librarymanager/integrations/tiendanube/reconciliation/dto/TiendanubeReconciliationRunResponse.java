package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto;

import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationSource;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationStatus;

import java.time.Instant;

public record TiendanubeReconciliationRunResponse(
        Long id,
        Long storeId,
        TiendanubeReconciliationSource source,
        TiendanubeReconciliationStatus status,
        int linkedCount,
        int checkedCount,
        int issueCount,
        String lastErrorType,
        String lastErrorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
}
