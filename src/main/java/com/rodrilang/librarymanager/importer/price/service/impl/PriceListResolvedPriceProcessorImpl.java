package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.importer.price.dto.internal.PriceImportCounters;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListResolvedPrice;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportPriceStagingRepository;
import com.rodrilang.librarymanager.importer.price.service.PriceListResolvedPriceProcessor;
import com.rodrilang.librarymanager.service.EditorialPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PriceListResolvedPriceProcessorImpl implements PriceListResolvedPriceProcessor {

    private static final int BATCH_SIZE = 1000;

    private final PriceListImportPriceStagingRepository stagingRepository;
    private final EditorialPriceService editorialPriceService;

    @Override
    public PriceImportCounters process(
            Long jobId
    ) {
        long afterId = 0L;

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int skipped = 0;

        while (true) {

            List<PriceListResolvedPrice> prices =
                    stagingRepository.findBatch(
                            jobId,
                            afterId,
                            BATCH_SIZE
                    );

            if (prices.isEmpty()) {
                break;
            }

            PriceImportCounters counters =
                    editorialPriceService
                            .registerResolvedPrices(
                                    jobId,
                                    prices
                            );

            created += counters.createdPrices();
            updated += counters.updatedPrices();
            unchanged += counters.unchangedPrices();
            skipped += counters.skippedRows();

            afterId =
                    prices.getLast()
                            .stagingId();
        }

        return new PriceImportCounters(
                created,
                updated,
                unchanged,
                skipped
        );
    }
}
