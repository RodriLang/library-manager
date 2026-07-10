package com.rodrilang.librarymanager.importer.price.dto;

public record PriceImportCounters(
        int createdPrices,
        int updatedPrices,
        int unchangedPrices
) {
}