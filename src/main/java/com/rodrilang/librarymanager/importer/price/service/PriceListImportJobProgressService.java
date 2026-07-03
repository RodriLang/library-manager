package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.importer.price.dto.PriceListImportError;

import java.util.List;

public interface PriceListImportJobProgressService {

    void markProcessing(Long jobId);

    void updateTotalRows(Long jobId, int totalRows, int errorCount);

    void updateProgress(
            Long jobId,
            int processedRows,
            int createdBooks,
            int updatedBooks
    );

    void markCompleted(
            Long jobId,
            int processedRows,
            int createdBooks,
            int updatedBooks,
            int errorCount
    );

    void markFailed(Long jobId, String errorMessage);

    void saveErrors(Long jobId, List<PriceListImportError> errors);

}