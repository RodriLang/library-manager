package com.rodrilang.librarymanager.inventory.pricing.job.dto;

import com.rodrilang.librarymanager.inventory.pricing.job.model.InventoryPriceImportJobStatus;
import com.rodrilang.librarymanager.inventory.pricing.job.model.InventoryPriceImportJobType;

import java.time.Instant;

public record InventoryPriceImportJobResponse(
        Long id,
        InventoryPriceImportJobType type,
        InventoryPriceImportJobStatus status,
        int totalRows,
        int processedRows,
        int successRows,
        int skippedRows,
        int errorRows,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage
) {}
