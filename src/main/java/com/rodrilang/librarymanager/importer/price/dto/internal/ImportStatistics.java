package com.rodrilang.librarymanager.importer.price.dto.internal;

public record ImportStatistics(
        int processedRows,
        int processedBooks,
        int duplicateBookRows,
        int createdBooks,
        int createdPrices,
        int updatedPrices,
        int unchangedPrices,
        int skippedPrices,
        int errors
) {
}