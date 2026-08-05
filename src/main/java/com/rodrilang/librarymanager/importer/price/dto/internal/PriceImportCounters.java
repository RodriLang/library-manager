package com.rodrilang.librarymanager.importer.price.dto.internal;

public record PriceImportCounters(
        int createdPrices,
        int updatedPrices,
        int unchangedPrices,
        int skippedRows
) {
}