package com.rodrilang.librarymanager.importer.price.dto.internal;

public record ImportStatistics(
        int processedRows,
        int createdBooks,
        int createdPrices,
        int updatedPrices,
        int unchangedPrices,
        int skippedRows,
        int errors
) {
}
