package com.rodrilang.librarymanager.inventory.pricing.job.service;

import com.rodrilang.librarymanager.inventory.pricing.job.dto.InventoryPriceImportJobResponse;
import com.rodrilang.librarymanager.inventory.pricing.job.model.*;
import com.rodrilang.librarymanager.inventory.pricing.job.repository.InventoryPriceImportJobRepository;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImport;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportStatus;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryPriceImportJobService {
    private static final int MAX_ERROR = 2000;
    private final InventoryPriceImportJobRepository repository;
    private final InventoryPriceImportRepository importRepository;

    @Transactional
    public InventoryPriceImportJob ensureProviderCatalogSync(long importId, int totalRows) {
        return ensure(importId, InventoryPriceImportJobType.PROVIDER_CATALOG_SYNC, totalRows);
    }

    @Transactional
    public InventoryPriceImportJob ensureInventoryApply(long importId, int totalRows, Long userId) {
        InventoryPriceImportJob job = ensure(importId, InventoryPriceImportJobType.INVENTORY_PRICE_APPLY, totalRows);
        job.setRequestedByUserId(userId);
        return job;
    }

    private InventoryPriceImportJob ensure(long importId, InventoryPriceImportJobType type, int totalRows) {
        return repository.findByPriceImportIdAndType(importId, type).orElseGet(() -> {
            InventoryPriceImport priceImport = importRepository.getReferenceById(importId);
            return repository.save(InventoryPriceImportJob.builder()
                    .priceImport(priceImport).type(type).status(InventoryPriceImportJobStatus.PENDING)
                    .totalRows(Math.max(0, totalRows)).build());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void batchCompleted(long jobId, long lastStagingId, int processed, int success, int skipped, int errors) {
        InventoryPriceImportJob job = repository.findById(jobId).orElseThrow();
        job.setProcessedRows(job.getProcessedRows() + processed);
        job.setSuccessRows(job.getSuccessRows() + success);
        job.setSkippedRows(job.getSkippedRows() + skipped);
        job.setErrorRows(job.getErrorRows() + errors);
        job.setLastStagingId(lastStagingId);
        job.setHeartbeatAt(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completed(long jobId) {
        InventoryPriceImportJob job = repository.findById(jobId).orElseThrow();
        job.setStatus(InventoryPriceImportJobStatus.COMPLETED);
        job.setFinishedAt(Instant.now());
        job.setHeartbeatAt(Instant.now());
        job.setErrorMessage(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(long jobId, Throwable error) {
        InventoryPriceImportJob job = repository.findById(jobId).orElse(null);
        if (job == null) return;
        job.setStatus(InventoryPriceImportJobStatus.FAILED);
        job.setFinishedAt(Instant.now());
        job.setHeartbeatAt(Instant.now());
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        job.setErrorMessage(message.length() <= MAX_ERROR ? message : message.substring(0, MAX_ERROR));
    }

    @Transactional
    public int requeueStale() {
        return repository.requeueStale(Instant.now().minus(Duration.ofMinutes(15)));
    }

    @Transactional
    public int failStalePreviews() {
        return importRepository.failStalePreviewProcessing(Instant.now().minus(Duration.ofMinutes(30)));
    }


    @Transactional
    public InventoryPriceImportJobResponse retry(long importId, InventoryPriceImportJobType type) {
        InventoryPriceImportJob job = repository.findByPriceImportIdAndType(importId, type).orElseThrow();
        if (job.getStatus() != InventoryPriceImportJobStatus.FAILED) {
            throw new BusinessException("Sólo se puede reintentar un trabajo fallido.");
        }
        if (type == InventoryPriceImportJobType.INVENTORY_PRICE_APPLY) {
            InventoryPriceImport priceImport = importRepository.findById(importId).orElseThrow();
            priceImport.setStatus(InventoryPriceImportStatus.PROCESSING);
            priceImport.setProcessingStartedAt(Instant.now());
            priceImport.setProcessingFinishedAt(null);
            priceImport.setProcessingError(null);
        }
        job.setStatus(InventoryPriceImportJobStatus.PENDING);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        job.setHeartbeatAt(null);
        job.setErrorMessage(null);
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public List<InventoryPriceImportJobResponse> list(long importId) {
        return repository.findAllByPriceImportIdOrderByIdAsc(importId).stream().map(this::toResponse).toList();
    }

    private InventoryPriceImportJobResponse toResponse(InventoryPriceImportJob j) {
        return new InventoryPriceImportJobResponse(j.getId(), j.getType(), j.getStatus(), j.getTotalRows(),
                j.getProcessedRows(), j.getSuccessRows(), j.getSkippedRows(), j.getErrorRows(),
                j.getStartedAt(), j.getFinishedAt(), j.getErrorMessage());
    }
}
