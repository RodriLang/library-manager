package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.configuration.repository.PriceListImportConfigRepository;
import com.rodrilang.librarymanager.importer.price.configuration.repository.PriceListProviderRepository;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportJobErrorResponse;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportJobStatusResponse;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportStartResponse;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobStatus;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobErrorRepository;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.importer.price.service.PriceListAsyncProcessor;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportService;
import com.rodrilang.librarymanager.importer.price.validator.PriceListImportDateValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PriceListImportServiceImpl implements PriceListImportService {

    private final PriceListImportJobRepository jobRepository;
    private final PriceListImportJobErrorRepository errorRepository;
    private final PriceListAsyncProcessor asyncProcessor;
    private final PriceListProviderRepository providerRepository;
    private final PriceListImportConfigRepository configRepository;

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
                .orElseThrow(() ->
                        new BusinessException(
                                "No se encontró el proveedor seleccionado."
                        )
                );

        if (!provider.isActive()) {
            throw new BusinessException(
                    "El proveedor seleccionado está inactivo."
            );
        }

        PriceListImportConfig importConfig =
                configRepository.findFirstByProviderIdAndActiveTrue(providerId)
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
                .orElseThrow(() ->
                        new BusinessException(
                                "No se encontró la importación solicitada."
                        )
                );

        int progress = job.getTotalRows() == 0
                ? 0
                : (job.getProcessedRows() * 100)
                  / job.getTotalRows();

        List<PriceListImportJobErrorResponse> errors =
                errorRepository.findByJobIdOrderByRowNumberAsc(jobId)
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

        return new PriceListImportJobStatusResponse(
                job.getId(),
                job.getStatus(),
                job.getTotalRows(),
                job.getProcessedRows(),
                job.getCreatedBooks(),
                job.getCreatedPrices(),
                job.getUpdatedPrices(),
                job.getUnchangedPrices(),
                progress,
                job.getErrorMessage(),
                errors
        );
    }

    private PriceListImportStartResponse createAndStartJob(
            PriceListProvider provider,
            PriceListImportConfig importConfig,
            MultipartFile file,
            LocalDate validFrom,
            String idempotencyKey
    ) {
        byte[] fileBytes = readFile(file);

        PriceListImportJob job = PriceListImportJob.builder()
                .idempotencyKey(idempotencyKey)
                .provider(provider)
                .importConfig(importConfig)
                .priceListSource(null)
                .validFrom(validFrom)
                .status(PriceListImportJobStatus.PENDING)
                .totalRows(0)
                .processedRows(0)
                .createdBooks(0)
                .createdPrices(0)
                .updatedPrices(0)
                .unchangedPrices(0)
                .errorCount(0)
                .createdAt(Instant.now())
                .build();

        PriceListImportJob savedJob =
                jobRepository.save(job);

        startProcessingAfterCommit(
                savedJob.getId(),
                fileBytes
        );

        return new PriceListImportStartResponse(
                savedJob.getId(),
                savedJob.getStatus(),
                "La importación fue iniciada."
        );
    }

    private void startProcessingAfterCommit(
            Long jobId,
            byte[] fileBytes
    ) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {

            asyncProcessor.process(jobId, fileBytes);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        asyncProcessor.process(
                                jobId,
                                fileBytes
                        );
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

    private byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    "Debe seleccionar un archivo Excel."
            );
        }

        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(
                    "No se pudo leer el archivo Excel: "
                            + exception.getMessage()
            );
        }
    }
}