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
import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobErrorRepository;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.importer.price.service.PriceListAsyncProcessor;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportService;
import com.rodrilang.librarymanager.importer.price.validator.PriceListImportDateValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
            PriceListSource priceListSource,
            MultipartFile file,
            LocalDate validFrom,
            String idempotencyKey
    ) {
        PriceListImportDateValidator.validateValidFrom(validFrom);

        return jobRepository.findByIdempotencyKey(idempotencyKey)
                .map(existingJob -> toExistingJobResponse(existingJob))
                .orElseGet(() -> createAndStartLegacyJob(priceListSource, file, validFrom, idempotencyKey));
    }

    @Override
    @Transactional(readOnly = true)
    public PriceListImportJobStatusResponse getStatus(Long jobId) {
        PriceListImportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("No se encontró la importación solicitada."));

        int progress = job.getTotalRows() == 0
                ? 0
                : (job.getProcessedRows() * 100) / job.getTotalRows();

        List<PriceListImportJobErrorResponse> errors = errorRepository.findByJobIdOrderByRowNumberAsc(jobId)
                .stream()
                .map(error -> new PriceListImportJobErrorResponse(
                        error.getRowNumber(),
                        error.getIsbn(),
                        error.getMessage(),
                        error.getSeverity()
                ))
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

    @Override
    @Transactional
    public PriceListImportStartResponse startProviderImport(
            Long providerId,
            MultipartFile file,
            LocalDate validFrom,
            String idempotencyKey
    ) {
        PriceListImportDateValidator.validateValidFrom(validFrom);

        return jobRepository.findByIdempotencyKey(idempotencyKey)
                .map(this::toExistingJobResponse)
                .orElseGet(() -> createAndStartProviderJob(providerId, file, validFrom, idempotencyKey));
    }

    private PriceListImportStartResponse createAndStartLegacyJob(
            PriceListSource priceListSource,
            MultipartFile file,
            LocalDate validFrom,
            String idempotencyKey
    ) {
        PriceListImportJob job = PriceListImportJob.builder()
                .idempotencyKey(idempotencyKey)
                .priceListSource(priceListSource)
                .validFrom(validFrom)
                .status(PriceListImportJobStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        return saveAndStart(job, file);
    }

    private PriceListImportStartResponse createAndStartProviderJob(
            Long providerId,
            MultipartFile file,
            LocalDate validFrom,
            String idempotencyKey
    ) {
        PriceListProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new BusinessException("No se encontró el proveedor solicitado."));

        if (!provider.isActive()) {
            throw new BusinessException("El proveedor se encuentra inactivo.");
        }

        PriceListImportConfig config = configRepository.findFirstByProviderIdAndActiveTrue(providerId)
                .orElseThrow(() -> new BusinessException("El proveedor no tiene una configuración activa."));

        PriceListImportJob job = PriceListImportJob.builder()
                .idempotencyKey(idempotencyKey)
                .provider(provider)
                .importConfig(config)
                .validFrom(validFrom)
                .status(PriceListImportJobStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        return saveAndStart(job, file);
    }

    private PriceListImportStartResponse saveAndStart(PriceListImportJob job, MultipartFile file) {
        byte[] fileBytes = readFile(file);
        PriceListImportJob savedJob = jobRepository.save(job);

        asyncProcessor.process(savedJob.getId(), fileBytes);

        return new PriceListImportStartResponse(
                savedJob.getId(),
                savedJob.getStatus(),
                "La importación fue iniciada."
        );
    }

    private PriceListImportStartResponse toExistingJobResponse(PriceListImportJob job) {
        return new PriceListImportStartResponse(
                job.getId(),
                job.getStatus(),
                "La importación ya había sido iniciada."
        );
    }

    private byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Debe seleccionar un archivo Excel.");
        }

        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer el archivo Excel: " + ex.getMessage());
        }
    }
}