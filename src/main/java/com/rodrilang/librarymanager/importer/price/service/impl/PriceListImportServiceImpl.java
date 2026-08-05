package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.configuration.repository.PriceListImportConfigRepository;
import com.rodrilang.librarymanager.importer.price.configuration.repository.PriceListProviderRepository;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportJobErrorResponse;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportJobStatusResponse;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportStartResponse;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobStatus;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobErrorRepository;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.importer.price.service.PriceListAsyncProcessor;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportService;
import com.rodrilang.librarymanager.importer.price.storage.PriceListImportFileStorage;
import com.rodrilang.librarymanager.importer.price.validator.PriceListImportDateValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PriceListImportServiceImpl implements PriceListImportService {

    private static final int MAX_STATUS_ERRORS = 100;

    private final PriceListImportJobRepository jobRepository;
    private final PriceListImportJobErrorRepository errorRepository;
    private final PriceListAsyncProcessor asyncProcessor;
    private final PriceListProviderRepository providerRepository;
    private final PriceListImportConfigRepository configRepository;
    private final PriceListImportFileStorage fileStorage;

    @Override
    @Transactional
    public PriceListImportStartResponse startImport(
            Long providerId,
            MultipartFile file,
            LocalDate validFrom,
            String idempotencyKey
    ) {
        PriceListImportDateValidator.validateValidFrom(validFrom);

        PriceListImportJob existingJob =
                jobRepository.findByIdempotencyKey(idempotencyKey)
                        .orElse(null);

        if (existingJob != null) {
            return toExistingJobResponse(existingJob);
        }

        PriceListProvider provider = providerRepository.findById(providerId)
                        .orElseThrow(() -> new BusinessException("No se encontró el proveedor seleccionado."));

        if (!provider.isActive()) {
            throw new BusinessException("El proveedor seleccionado está inactivo.");
        }

        PriceListImportConfig importConfig =
                configRepository
                        .findFirstByProviderIdAndActiveTrue(providerId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "El proveedor seleccionado no tiene una configuración de importación activa."
                                )
                        );

        return createAndStartJob(
                provider,
                importConfig,
                file,
                validFrom,
                idempotencyKey
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PriceListImportJobStatusResponse getStatus(Long jobId) {
        PriceListImportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("No se encontró la importación solicitada."));

        List<PriceListImportJobErrorResponse> errors =
                shouldIncludeErrors(job.getStatus())
                        ? loadErrors(jobId)
                        : List.of();

        return new PriceListImportJobStatusResponse(
                job.getId(),
                job.getStatus(),
                job.getTotalRows(),
                job.getProcessedRows(),
                job.getCreatedBooks(),
                job.getCreatedPrices(),
                job.getUpdatedPrices(),
                job.getUnchangedPrices(),
                job.getSkippedRows(),
                calculateProgress(job),
                job.getErrorCount(),
                job.getErrorMessage(),
                errors
        );
    }

    private int calculateProgress(PriceListImportJob job) {
        if (job.getStatus() == PriceListImportJobStatus.COMPLETED) {
            return 100;
        }

        if (job.getTotalRows() <= 0) {
            return 0;
        }

        long percentage =
                ((long) job.getProcessedRows() * 100)
                        / job.getTotalRows();

        return (int) Math.min(percentage, 100);
    }

    private boolean shouldIncludeErrors(
            PriceListImportJobStatus status
    ) {
        return status == PriceListImportJobStatus.COMPLETED
                || status == PriceListImportJobStatus.FAILED;
    }

    private List<PriceListImportJobErrorResponse> loadErrors(Long jobId) {
        Pageable firstPage = PageRequest.of(0, MAX_STATUS_ERRORS);

        return errorRepository
                .findByJobIdOrderByRowNumberAsc(
                        jobId,
                        firstPage
                )
                .stream()
                .map(error ->
                        new PriceListImportJobErrorResponse(
                                error.getRowNumber(),
                                error.getIsbn(),
                                error.getMessage(),
                                error.getSeverity()
                        )
                )
                .toList();
    }

    private PriceListImportStartResponse createAndStartJob(
            PriceListProvider provider,
            PriceListImportConfig importConfig,
            MultipartFile file,
            LocalDate validFrom,
            String idempotencyKey
    ) {
        Path filePath = fileStorage.store(file);

        try {
            PriceListImportJob job =
                    PriceListImportJob.builder()
                            .idempotencyKey(idempotencyKey)
                            .provider(provider)
                            .importConfig(importConfig)
                            .validFrom(validFrom)
                            .status(PriceListImportJobStatus.PENDING)
                            .totalRows(0)
                            .processedRows(0)
                            .createdBooks(0)
                            .createdPrices(0)
                            .updatedPrices(0)
                            .unchangedPrices(0)
                            .skippedRows(0)
                            .errorCount(0)
                            .createdAt(Instant.now())
                            .build();

            PriceListImportJob savedJob =
                    jobRepository.save(job);

            startProcessingAfterCommit(
                    savedJob.getId(),
                    filePath
            );

            return new PriceListImportStartResponse(
                    savedJob.getId(),
                    savedJob.getStatus(),
                    "La importación fue iniciada."
            );

        } catch (RuntimeException exception) {
            fileStorage.deleteQuietly(filePath);
            throw exception;
        }
    }

    private void startProcessingAfterCommit(
            Long jobId,
            Path filePath
    ) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {

            asyncProcessor.process(jobId, filePath);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        try {
                            asyncProcessor.process(
                                    jobId,
                                    filePath
                            );
                        } catch (RuntimeException exception) {
                            fileStorage.deleteQuietly(filePath);
                            throw exception;
                        }
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (
                                status != TransactionSynchronization
                                        .STATUS_COMMITTED
                        ) {
                            fileStorage.deleteQuietly(filePath);
                        }
                    }
                }
        );
    }

    private PriceListImportStartResponse toExistingJobResponse(
            PriceListImportJob job
    ) {
        return new PriceListImportStartResponse(
                job.getId(),
                job.getStatus(),
                "La importación ya había sido iniciada."
        );
    }
}