package com.rodrilang.librarymanager.inventory.pricing.dto;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportStatus;

import java.time.Instant;
import java.time.LocalDate;

public record InventoryPriceImportHistoryResponse(
        Long id,
        String originalFilename,
        String formatName,
        String sourceName,
        LocalDate effectiveFrom,
        InventoryPriceImportStatus status,
        InventoryPriceImportSummaryResponse summary,
        Instant createdAt,
        Instant appliedAt
) {
}
