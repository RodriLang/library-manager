package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.ImportStatistics;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportError;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobError;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobStatus;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobErrorRepository;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportJobProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PriceListImportJobProgressServiceImpl implements PriceListImportJobProgressService {

    private final PriceListImportJobRepository jobRepository;
    private final PriceListImportJobErrorRepository errorRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void markProcessing(Long jobId) {
        PriceListImportJob job = getJob(jobId);
        job.setStatus(PriceListImportJobStatus.PROCESSING);
        job.setStartedAt(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void updateTotalRows(Long jobId, int totalRows, int errorCount) {
        PriceListImportJob job = getJob(jobId);
        job.setTotalRows(totalRows);
        job.setErrorCount(errorCount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void updateProgress(Long jobId, ImportStatistics importStatistics) {
        PriceListImportJob job = getJob(jobId);
        job.setProcessedRows(importStatistics.processedRows());
        job.setCreatedBooks(importStatistics.createdBooks());
        job.setCreatedPrices(importStatistics.createdPrices());
        job.setUpdatedPrices(importStatistics.updatedPrices());
        job.setErrorCount(importStatistics.errors());
        job.setUnchangedPrices(importStatistics.unchangedPrices());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void markCompleted(Long jobId, ImportStatistics importStatistics) {
        PriceListImportJob job = getJob(jobId);
        job.setStatus(PriceListImportJobStatus.COMPLETED);
        job.setProcessedRows(importStatistics.processedRows());
        job.setCreatedBooks(importStatistics.createdBooks());
        job.setCreatedPrices(importStatistics.createdPrices());
        job.setUpdatedPrices(importStatistics.updatedPrices());
        job.setErrorCount(importStatistics.errors());
        job.setUnchangedPrices(importStatistics.unchangedPrices());
        job.setFinishedAt(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void markFailed(Long jobId, String errorMessage) {
        PriceListImportJob job = getJob(jobId);
        job.setStatus(PriceListImportJobStatus.FAILED);
        job.setErrorMessage(errorMessage);
        job.setFinishedAt(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveErrors(Long jobId, List<PriceListImportError> errors) {
        if (errors == null || errors.isEmpty()) {
            return;
        }

        PriceListImportJob job = getJob(jobId);

        List<PriceListImportJobError> jobErrors = errors.stream()
                .map(error -> PriceListImportJobError.builder()
                        .job(job)
                        .rowNumber(error.rowNumber())
                        .isbn(error.isbn())
                        .message(error.message())
                        .severity(error.severity())
                        .build())
                .toList();

        errorRepository.saveAll(jobErrors);
    }

    private PriceListImportJob getJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("No se encontró la importación solicitada."));
    }
}