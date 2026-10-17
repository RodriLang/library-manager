package com.rodrilang.librarymanager.inventory.pricing.job.service;

import com.rodrilang.librarymanager.inventory.pricing.job.repository.InventoryPriceImportJobJdbcRepository;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceImportApplyWorker;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceImportApplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryPriceImportJobRunner {
    private final InventoryPriceImportApplyWorker applyWorker;
    private final ProviderCatalogSyncJobProcessor providerProcessor;
    private final InventoryPriceImportApplyService applyService;
    private final InventoryPriceImportJobService jobService;

    @Async("inventoryPriceApplyExecutor")
    public void runInventoryApply(InventoryPriceImportJobJdbcRepository.ClaimedJob job) {
        try {
            applyWorker.process(job.importId(), job.requestedByUserId(), job.id(), job.lastStagingId());
            jobService.completed(job.id());
        } catch (Exception ex) {
            log.error("PRICE INVENTORY APPLY JOB FAILED jobId={} importId={}", job.id(), job.importId(), ex);
            jobService.failed(job.id(), ex);
            applyService.markFailed(job.importId(), ex);
        }
    }

    @Async("providerCatalogSyncExecutor")
    public void runProviderCatalogSync(InventoryPriceImportJobJdbcRepository.ClaimedJob job) {
        try {
            providerProcessor.process(job.id(), job.importId(), job.lastStagingId());
        } catch (Exception ex) {
            log.error("PRICE PROVIDER CATALOG JOB FAILED jobId={} importId={}", job.id(), job.importId(), ex);
            jobService.failed(job.id(), ex);
        }
    }
}
