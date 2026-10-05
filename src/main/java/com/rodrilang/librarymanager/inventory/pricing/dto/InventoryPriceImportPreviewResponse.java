package com.rodrilang.librarymanager.inventory.pricing.dto;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportStatus;

import java.time.LocalDate;
import java.util.List;

public record InventoryPriceImportPreviewResponse(
        Long importId,
        String originalFilename,
        String formatName,
        Long providerId,
        String providerName,
        String sourceName,
        LocalDate effectiveFrom,
        InventoryPriceImportStatus status,
        InventoryPriceImportSummaryResponse summary,
        List<InventoryPriceImportItemResponse> items
) {
}
