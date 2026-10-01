package com.rodrilang.librarymanager.inventory.pricing.dto;

public record InventoryPriceImportSummaryResponse(
        int totalRows,
        int matchedRows,
        int unmatchedRows,
        int newPriceRows,
        int increaseRows,
        int decreaseRows,
        int unchangedRows,
        int conflictRows,
        int reviewRows,
        int appliedRows
) {
}
