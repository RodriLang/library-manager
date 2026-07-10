package com.rodrilang.librarymanager.importer.price.dto;

public record ImportStatistics(
        int processedRows,
        int createdBooks,
        int createdPrices,
        int updatedPrices,
        int unchangedPrices,
        int errors
) {}
