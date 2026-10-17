package com.rodrilang.librarymanager.inventory.pricing.dto;

import com.rodrilang.librarymanager.inventory.pricing.job.dto.InventoryPriceImportJobResponse;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record InventoryPriceImportActivityResponse(
        Long id,
        Long formatId,
        String formatName,
        Long providerId,
        String providerName,
        String sourceName,
        String originalFilename,
        LocalDate effectiveFrom,
        InventoryPriceImportStatus status,
        InventoryPriceImportSummaryResponse summary,
        Instant createdAt,
        Instant appliedAt,
        Instant processingStartedAt,
        Instant processingFinishedAt,
        String processingError,
        List<InventoryPriceImportJobResponse> jobs
) {
}
