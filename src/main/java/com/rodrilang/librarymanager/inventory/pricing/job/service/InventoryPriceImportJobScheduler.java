package com.rodrilang.librarymanager.inventory.pricing.job.service;

import com.rodrilang.librarymanager.inventory.pricing.job.model.InventoryPriceImportJobType;
import com.rodrilang.librarymanager.inventory.pricing.job.repository.InventoryPriceImportJobJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryPriceImportJobScheduler {
    private final InventoryPriceImportJobJdbcRepository jdbcRepository;
    private final InventoryPriceImportJobService jobService;
    private final InventoryPriceImportJobRunner runner;

    @Scheduled(fixedDelayString = "${app.price-import.jobs.poll-ms:1000}")
    public void dispatch() {
        jdbcRepository.claimNext(InventoryPriceImportJobType.INVENTORY_PRICE_APPLY)
                .ifPresent(runner::runInventoryApply);
        jdbcRepository.claimNext(InventoryPriceImportJobType.PROVIDER_CATALOG_SYNC)
                .ifPresent(runner::runProviderCatalogSync);
    }

    @Scheduled(fixedDelayString = "${app.price-import.jobs.recovery-ms:60000}", initialDelay = 30000)
    public void recoverStale() {
        int recovered = jobService.requeueStale();
        int failedPreviews = jobService.failStalePreviews();
        if (recovered > 0) log.warn("Requeued {} stale price import jobs", recovered);
        if (failedPreviews > 0) log.warn("Marked {} stale price import previews as failed", failedPreviews);
    }
}
