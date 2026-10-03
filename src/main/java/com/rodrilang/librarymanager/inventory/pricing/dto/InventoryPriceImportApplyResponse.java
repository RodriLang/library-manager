package com.rodrilang.librarymanager.inventory.pricing.dto;

public record InventoryPriceImportApplyResponse(
        Long importId,
        int appliedRows,
        int skippedRows
) {
}
