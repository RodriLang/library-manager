package com.rodrilang.librarymanager.catalog.candidate.dto.response;

import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidateStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountPurpose;

import java.time.Instant;

public record CatalogCandidateResponse(

        Long id,

        String isbn10,

        String isbn13,

        CatalogCandidateStatus status,

        Long resolvedBookId,

        String resolvedBookTitle,

        Integer pendingInventoryUnits,

        Integer pendingInventorySessions,

        Long latestInventoryCountSessionId,

        InventoryCountPurpose latestInventoryCountPurpose,

        boolean resolutionReused,

        Instant firstDetectedAt,

        Instant resolvedAt

) {
}
