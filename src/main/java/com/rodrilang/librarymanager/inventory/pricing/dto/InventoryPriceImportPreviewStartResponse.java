package com.rodrilang.librarymanager.inventory.pricing.dto;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportStatus;

import java.time.Instant;

public record InventoryPriceImportPreviewStartResponse(
        Long importId,
        InventoryPriceImportStatus status,
        Instant startedAt
) {
}
