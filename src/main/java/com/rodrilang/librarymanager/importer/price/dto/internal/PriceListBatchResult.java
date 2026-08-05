package com.rodrilang.librarymanager.importer.price.dto.internal;

public record PriceListBatchResult(
        int processedRows,
        int createdBooks,
        int createdPrices,
        int updatedPrices,
        int unchangedPrices,
        int skippedRows
) {
}