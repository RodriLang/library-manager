package com.rodrilang.librarymanager.inventory.pricing.dto;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportStatus;

import java.time.Instant;

public record InventoryPriceImportProcessingStatusResponse(
        Long importId,
        InventoryPriceImportStatus status,
        Instant startedAt,
        Instant finishedAt,
        Integer appliedRows,
        Integer skippedRows,
        String errorMessage
) {
}
