package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto;

import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisRunStatus;

import java.time.Instant;

public record TiendanubeImportAnalysisRunResponse(
        Long id,
        TiendanubeImportAnalysisRunStatus status,
        int totalCount,
        int readyToLinkCount,
        int requiresReviewCount,
        int notInInventoryCount,
        int notInCatalogCount,
        int conflictCount,
        int alreadyLinkedCount,
        int resolvedCount,
        int ignoredCount,
        int missingIdentifierCount,
        String lastErrorType,
        String lastErrorMessage,
        Instant createdAt,
        Instant completedAt
) {
}
