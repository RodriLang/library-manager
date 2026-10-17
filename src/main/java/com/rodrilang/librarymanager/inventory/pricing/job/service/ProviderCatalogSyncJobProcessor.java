package com.rodrilang.librarymanager.inventory.pricing.job.service;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImport;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportProviderRowJdbcRepository;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportRepository;
import com.rodrilang.librarymanager.provider.bookstore.model.BookstoreProvider;
import com.rodrilang.librarymanager.provider.bookstore.repository.BookstoreProviderRepository;
import com.rodrilang.librarymanager.purchasing.service.BookstoreProviderCatalogBatchService;
import com.rodrilang.librarymanager.purchasing.repository.BookstoreProviderCatalogJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderCatalogSyncJobProcessor {
    private static final int BATCH_SIZE = 500;
    private final InventoryPriceImportRepository importRepository;
    private final InventoryPriceImportProviderRowJdbcRepository stagingRepository;
    private final BookstoreProviderCatalogBatchService batchService;
    private final InventoryPriceImportJobService jobService;
    private final BookstoreProviderRepository bookstoreProviderRepository;
    private final BookstoreProviderCatalogJdbcRepository catalogJdbcRepository;

    public void process(long jobId, long importId, long initialCursor) {
        InventoryPriceImport priceImport = importRepository.findDetailedById(importId).orElseThrow();
        if (priceImport.getProvider() == null) {
            jobService.completed(jobId);
            return;
        }
        long bookstoreId = priceImport.getBookstore().getId();
        long providerId = priceImport.getProvider().getId();
        ensureBookstoreProvider(priceImport);

        long cursor = initialCursor;
        while (true) {
            List<InventoryPriceImportProviderRowJdbcRepository.StoredRow> batch =
                    stagingRepository.findBatch(importId, cursor, BATCH_SIZE);
            if (batch.isEmpty()) break;
            long started = System.nanoTime();
            var result = batchService.process(importId, bookstoreId, providerId, priceImport.getEffectiveFrom(), batch);
            cursor = batch.getLast().id();
            jobService.batchCompleted(jobId, cursor, result.processed(), result.resolved(), result.skipped(), result.errors());
            log.info("PRICE PROVIDER SYNC importId={} jobId={} cursor={} processed={} resolved={} skipped={} timeMs={}",
                    importId, jobId, cursor, result.processed(), result.resolved(), result.skipped(),
                    (System.nanoTime() - started) / 1_000_000);
        }
        long finalizeStarted = System.nanoTime();
        int terms = catalogJdbcRepository.upsertBookstoreTermsFromStaging(
                bookstoreId, providerId, importId, priceImport.getEffectiveFrom());
        log.info("PRICE PROVIDER SYNC FINALIZE importId={} jobId={} terms={} timeMs={}",
                importId, jobId, terms, (System.nanoTime() - finalizeStarted) / 1_000_000);
        jobService.completed(jobId);
        stagingRepository.deleteByImportId(importId);
    }

    @Transactional
    protected void ensureBookstoreProvider(InventoryPriceImport priceImport) {
        var bookstore = priceImport.getBookstore();
        var provider = priceImport.getProvider();
        BookstoreProvider relation = bookstoreProviderRepository.findByBookstoreIdAndProviderId(bookstore.getId(), provider.getId())
                .orElseGet(() -> BookstoreProvider.builder().bookstore(bookstore).provider(provider).active(true).build());
        relation.setActive(true);
        bookstoreProviderRepository.save(relation);
    }
}
