package com.rodrilang.librarymanager.importer.price.dto;

import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobStatus;

import java.util.List;

public record PriceListImportJobStatusResponse(
        Long jobId,
        PriceListImportJobStatus status,
        int totalRows,
        int processedRows,
        int createdBooks,
        int createdPrices,
        int updatedPrices,
        int unchangedPrices,
        int progressPercentage,
        String errorMessage,
        List<PriceListImportJobErrorResponse> errors
) {
}