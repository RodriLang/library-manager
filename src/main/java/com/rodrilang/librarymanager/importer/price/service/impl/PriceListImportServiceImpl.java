package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PriceListImportServiceImpl implements PriceListImportService {

    private final PriceListImportJobRepository jobRepository;
    private final PriceListImportJobErrorRepository errorRepository;
    private final PriceListAsyncProcessor asyncProcessor;

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
                .map(existingJob -> new PriceListImportStartResponse(
                        existingJob.getId(),
                        existingJob.getStatus(),
                        "La importación ya había sido iniciada."
                ))
                .orElseGet(() -> createAndStartJob(priceListSource, file, validFrom, idempotencyKey));
    }

    private PriceListImportStartResponse createAndStartJob(
            PriceListSource priceListSource,
            MultipartFile file,
            LocalDate validFrom,
            String idempotencyKey
    ) {
        byte[] fileBytes;

        try {
            fileBytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer el archivo Excel: " + ex.getMessage());
        }

        PriceListImportJob job = PriceListImportJob.builder()
                .idempotencyKey(idempotencyKey)
                .priceListSource(priceListSource)
                .validFrom(validFrom)
                .status(PriceListImportJobStatus.PENDING)
                .createdAt(LocalDateTime.now(ZoneId.systemDefault()))
                .build();

        PriceListImportJob savedJob = jobRepository.save(job);

        asyncProcessor.process(savedJob.getId(), priceListSource, validFrom, fileBytes);

        return new PriceListImportStartResponse(
                savedJob.getId(),
                savedJob.getStatus(),
                "La importación fue iniciada."
        );
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
}