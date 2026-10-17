package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImport;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportStatus;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportItemRepository;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportRepository;
import com.rodrilang.librarymanager.inventory.pricing.job.service.InventoryPriceImportJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryPriceImportApplyWorker {
    private static final int BATCH_SIZE = 250;

    private final InventoryPriceImportRepository importRepository;
    private final InventoryPriceImportItemRepository itemRepository;
    private final InventoryPriceImportApplyBatchService batchService;
    private final InventoryPriceImportJobService jobService;

    public void process(Long importId, Long userId) {
        process(importId, userId, null, 0L);
    }

    public void process(Long importId, Long userId, Long jobId, long initialCursor) {
        InventoryPriceImport snapshot = importRepository.findDetailedById(importId)
                .orElseThrow(() -> new IllegalStateException("No se encontró la importación " + importId + "."));
        if (snapshot.getStatus() != InventoryPriceImportStatus.PROCESSING) return;

        long cursor = initialCursor;

        while (true) {
            var batch = itemRepository.findSelectedBatch(importId, cursor, PageRequest.of(0, BATCH_SIZE));
            if (batch.isEmpty()) break;
            long started = System.nanoTime();
            var result = batchService.process(importId, userId, batch);
            cursor = batch.getLast().getId();
            if (jobId != null) {
                jobService.batchCompleted(jobId, cursor, batch.size(), result.applied(), result.skipped(), 0);
            }
            log.info("PRICE INVENTORY APPLY PHASE importId={} cursor={} batch={} applied={} skipped={} timeMs={}",
                    importId, cursor, batch.size(), result.applied(), result.skipped(),
                    (System.nanoTime() - started) / 1_000_000);
        }
        int applied = Math.toIntExact(itemRepository.countByPriceImportIdAndAppliedTrue(importId));
        int considered = Math.toIntExact(itemRepository.countByPriceImportIdAndDiscardedFalse(importId));
        complete(importId, applied, Math.max(0, considered - applied));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long importId, int applied, int skipped) {
        InventoryPriceImport priceImport = importRepository.findById(importId).orElseThrow();
        if (priceImport.getStatus() != InventoryPriceImportStatus.PROCESSING) return;
        Instant finishedAt = Instant.now();
        priceImport.setAppliedRows(applied);
        priceImport.setSkippedRows(skipped);
        priceImport.setAppliedAt(finishedAt);
        priceImport.setProcessingFinishedAt(finishedAt);
        priceImport.setProcessingError(null);
        priceImport.setStatus(InventoryPriceImportStatus.APPLIED);
        importRepository.save(priceImport);
    }
}
