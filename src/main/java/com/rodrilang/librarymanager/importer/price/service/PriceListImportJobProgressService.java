package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.importer.price.dto.ImportStatistics;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportError;

import java.util.List;

public interface PriceListImportJobProgressService {

    void markProcessing(Long jobId);

    void updateTotalRows(Long jobId, int totalRows, int errorCount);

    void updateProgress(Long jobId, ImportStatistics importStatistics);

    void markCompleted(Long jobId, ImportStatistics importStatistics);

    void markFailed(Long jobId, String errorMessage);

    void saveErrors(Long jobId, List<PriceListImportError> errors);

}