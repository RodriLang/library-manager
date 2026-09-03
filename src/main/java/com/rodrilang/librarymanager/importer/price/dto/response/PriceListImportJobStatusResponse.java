package com.rodrilang.librarymanager.importer.price.dto.response;

import com.rodrilang.librarymanager.importer.price.enums.PriceListImportPhase;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobStatus;

public record PriceListImportJobStatusResponse(

        Long id,
        PriceListImportJobStatus status,
        PriceListImportPhase phase,
        int totalRows,
        int processedRows,
        int processedBooks,
        int duplicateBookRows,
        int processedPrices,
        int createdBooks,
        int createdPrices,
        int updatedPrices,
        int unchangedPrices,
        int skippedRows,
        int progressPercentage,
        int errorCount,
        String errorMessage
) {
}