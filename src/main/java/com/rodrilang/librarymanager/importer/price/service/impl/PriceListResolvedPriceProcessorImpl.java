package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.importer.price.dto.internal.PriceImportCounters;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListResolvedPrice;
import com.rodrilang.librarymanager.importer.price.exception.PriceListImportCancelledException;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportPriceStagingRepository;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportJobProgressService;
import com.rodrilang.librarymanager.importer.price.service.PriceListResolvedPriceProcessor;
import com.rodrilang.librarymanager.service.EditorialPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceListResolvedPriceProcessorImpl
        implements PriceListResolvedPriceProcessor {

    private static final int BATCH_SIZE = 1000;

    private final PriceListImportPriceStagingRepository stagingRepository;
    private final PriceListImportJobProgressService progressService;
    private final EditorialPriceService editorialPriceService;

    @Override
    public PriceImportCounters process(Long jobId) {
        long afterId = 0L;

        int processed = 0;
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int skipped = 0;

        while (true) {

            if (progressService.isCancellationRequested(jobId)) {
                throw new PriceListImportCancelledException();
            }

            List<PriceListResolvedPrice> prices =
                    stagingRepository.findBatch(
                            jobId,
                            afterId,
                            BATCH_SIZE
                    );

            if (prices.isEmpty()) {
                break;
            }

            long firstId =
                    prices.getFirst().stagingId();

            long lastId =
                    prices.getLast().stagingId();

            log.info(
                    "Resolved price batch started. "
                            + "jobId={} processed={} "
                            + "afterId={} rows={} "
                            + "firstId={} lastId={}",
                    jobId,
                    processed,
                    afterId,
                    prices.size(),
                    firstId,
                    lastId
            );

            PriceImportCounters counters =
                    editorialPriceService
                            .registerResolvedPrices(
                                    jobId,
                                    prices
                            );

            processed += prices.size();

            created +=
                    counters.createdPrices();

            updated +=
                    counters.updatedPrices();

            unchanged +=
                    counters.unchangedPrices();

            skipped +=
                    counters.skippedRows();

            afterId = lastId;

            progressService.updatePriceProgress(
                    jobId,
                    processed,
                    created,
                    updated,
                    unchanged,
                    skipped
            );

            log.info(
                    "Resolved price batch completed. "
                            + "jobId={} processed={} afterId={} "
                            + "created={} updated={} "
                            + "unchanged={} skipped={}",
                    jobId,
                    processed,
                    afterId,
                    created,
                    updated,
                    unchanged,
                    skipped
            );
        }

        log.info(
                "Resolved price processing completed. "
                        + "jobId={} processed={} "
                        + "created={} updated={} "
                        + "unchanged={} skipped={}",
                jobId,
                processed,
                created,
                updated,
                unchanged,
                skipped
        );

        return new PriceImportCounters(
                created,
                updated,
                unchanged,
                skipped
        );
    }
}