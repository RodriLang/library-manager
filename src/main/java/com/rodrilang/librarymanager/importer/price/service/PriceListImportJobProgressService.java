package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.importer.price.dto.internal.ImportStatistics;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportError;
import com.rodrilang.librarymanager.importer.price.enums.PriceListImportPhase;

import java.util.List;

public interface PriceListImportJobProgressService {

    void markProcessing(Long jobId);

    void updateTotalRows(Long jobId, int totalRows, int errorCount);

    void updateProgress(Long jobId, ImportStatistics importStatistics);

    void initializePriceProgress(Long jobId);

    void updatePriceProgress(
            Long jobId,
            int processedPrices,
            int createdPrices,
            int updatedPrices,
            int unchangedPrices,
            int skippedPrices
    );

    void markCompleted(Long jobId, ImportStatistics importStatistics);

    void markFailed(Long jobId, String errorMessage);

    void markCancelled(Long jobId);

    boolean isCancellationRequested(Long jobId);

    void saveErrors(Long jobId, List<PriceListImportError> errors);

    void updatePhase(Long jobId, PriceListImportPhase phase);
}