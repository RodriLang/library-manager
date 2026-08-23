package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.dto.internal.InventoryEditorialPriceSyncResult;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.internal.EditorialPriceInsertRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.EditorialPriceUpdateRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceImportCounters;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListResolvedPrice;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.repository.EditorialPriceBatchRepository;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubePriceSyncRequestedEvent;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.repository.EditorialPriceRepository;
import com.rodrilang.librarymanager.repository.InventoryEditorialPriceSyncRepository;
import com.rodrilang.librarymanager.repository.projection.EditorialPriceImportProjection;
import com.rodrilang.librarymanager.service.EditorialPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EditorialPriceServiceImpl implements EditorialPriceService {

    private final EditorialPriceRepository editorialPriceRepository;
    private final EditorialPriceBatchRepository batchRepository;
    private final PriceListImportJobRepository jobRepository;
    private final InventoryEditorialPriceSyncRepository inventoryEditorialPriceSyncRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public PriceImportCounters registerResolvedPrices(
            Long jobId,
            List<PriceListResolvedPrice> prices
    ) {
        if (prices == null || prices.isEmpty()) {
            return new PriceImportCounters(
                    0,
                    0,
                    0,
                    0
            );
        }

        long startedAt = System.nanoTime();

        log.info(
                "Resolved price batch started. jobId={} rows={}",
                jobId,
                prices.size()
        );

        PriceListImportJob job = jobRepository
                .findById(jobId)
                .orElseThrow(() -> new BusinessException("No se encontró el trabajo de importación."));

        log.info(
                "Resolved price batch job loaded. "
                        + "jobId={} providerId={} validFrom={}",
                jobId,
                job.getProvider().getId(),
                job.getValidFrom()
        );

        List<Long> bookIds = prices.stream()
                .map(PriceListResolvedPrice::bookId)
                .distinct()
                .toList();

        log.info(
                "Resolved price batch book ids prepared. "
                        + "jobId={} bookIds={}",
                jobId,
                bookIds.size()
        );

        long loadStartedAt = System.nanoTime();

        List<EditorialPriceImportProjection> existingPrices =
                editorialPriceRepository.findForImport(
                        bookIds,
                        job.getProvider().getId(),
                        job.getValidFrom()
                );

        log.info(
                "Resolved price existing prices loaded. "
                        + "jobId={} requestedBooks={} "
                        + "existingPrices={} time={}ms",
                jobId,
                bookIds.size(),
                existingPrices.size(),
                (System.nanoTime() - loadStartedAt)
                        / 1_000_000
        );

        Map<Long, EditorialPriceImportProjection> existingByBookId =
                existingPrices.stream()
                        .collect(
                                Collectors.toMap(
                                        EditorialPriceImportProjection::getBookId,
                                        Function.identity(),
                                        (existing, repeated) -> existing
                                )
                        );

        List<EditorialPriceInsertRow> toInsert = new ArrayList<>();

        List<EditorialPriceUpdateRow> toUpdate = new ArrayList<>();

        Set<Long> changedPriceBookIds = new LinkedHashSet<>();

        int unchanged = 0;

        long classificationStartedAt =
                System.nanoTime();

        for (PriceListResolvedPrice resolved : prices) {

            EditorialPriceImportProjection existing = existingByBookId.get(resolved.bookId());

            if (existing == null) {
                toInsert.add(
                        new EditorialPriceInsertRow(
                                resolved.bookId(),
                                resolved.selectedPrice()
                        )
                );

                changedPriceBookIds.add(resolved.bookId());

                continue;
            }

            if (
                    existing.getPrice().compareTo(resolved.selectedPrice()) == 0
                            && Boolean.TRUE.equals(existing.getActive())
            ) {
                unchanged++;
                continue;
            }

            toUpdate.add(
                    new EditorialPriceUpdateRow(
                            existing.getId(),
                            resolved.selectedPrice()
                    )
            );

            changedPriceBookIds.add(resolved.bookId());
        }

        log.info(
                "Resolved price batch classified. "
                        + "jobId={} inserts={} "
                        + "updates={} unchanged={} time={}ms",
                jobId,
                toInsert.size(),
                toUpdate.size(),
                unchanged,
                (System.nanoTime()
                        - classificationStartedAt)
                        / 1_000_000
        );

        long insertStartedAt = System.nanoTime();

        batchRepository.insertBatch(
                job.getProvider().getId(),
                job.getValidFrom(),
                toInsert
        );

        log.info(
                "Resolved price inserts completed. "
                        + "jobId={} rows={} time={}ms",
                jobId,
                toInsert.size(),
                (System.nanoTime()
                        - insertStartedAt)
                        / 1_000_000
        );

        long updateStartedAt = System.nanoTime();

        batchRepository.updateBatch(toUpdate);

        log.info(
                "Resolved price updates completed. "
                        + "jobId={} rows={} time={}ms",
                jobId,
                toUpdate.size(),
                (System.nanoTime()
                        - updateStartedAt)
                        / 1_000_000
        );

        long inventorySyncStartedAt = System.nanoTime();

        InventoryEditorialPriceSyncResult inventorySyncResult =
                inventoryEditorialPriceSyncRepository.syncCurrentPrices(
                        changedPriceBookIds,
                        LocalDate.now(ZoneId.systemDefault())
                );

        log.info(
                "Resolved price inventory sync completed. "
                        + "jobId={} changedBooks={} "
                        + "updatedInventories={} "
                        + "tiendanubeSyncRequested={}",
                jobId,
                changedPriceBookIds.size(),
                inventorySyncResult.updatedInventories(),
                inventorySyncResult
                        .tiendanubeSyncInventoryIds()
                        .size()
        );

        if (!inventorySyncResult
                .tiendanubeSyncInventoryIds()
                .isEmpty()) {

            eventPublisher.publishEvent(
                    new TiendanubePriceSyncRequestedEvent(
                            inventorySyncResult
                                    .tiendanubeSyncInventoryIds()
                    )
            );
        }

        int accounted = toInsert.size() + toUpdate.size() + unchanged;

        if (accounted != prices.size()) {
            log.warn(
                    "Resolved price batch counters mismatch. "
                            + "jobId={} rows={} accounted={} "
                            + "created={} updated={} unchanged={}",
                    jobId,
                    prices.size(),
                    accounted,
                    toInsert.size(),
                    toUpdate.size(),
                    unchanged
            );
        }

        log.info(
                "Resolved price batch completed. "
                        + "jobId={} rows={} "
                        + "created={} updated={} unchanged={} "
                        + "totalTime={}ms",
                jobId,
                prices.size(),
                toInsert.size(),
                toUpdate.size(),
                unchanged,
                (System.nanoTime() - startedAt)
                        / 1_000_000
        );

        return new PriceImportCounters(
                toInsert.size(),
                toUpdate.size(),
                unchanged,
                0
        );
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<EditorialPrice> findCurrentByBookId(Long bookId) {
        return editorialPriceRepository
                .findFirstByBookIdAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDescIdDesc(
                        bookId,
                        LocalDate.now(ZoneId.systemDefault())
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, EditorialPrice> findCurrentByBookIds(List<Long> bookIds) {
        return loadCurrentByBookIds(bookIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> findCurrentPricesByBookIds(List<Long> bookIds) {
        return loadCurrentByBookIds(bookIds)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getPrice()
                ));
    }

    private Map<Long, EditorialPrice> loadCurrentByBookIds(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Map.of();
        }

        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        return editorialPriceRepository
                .findCurrentByBookIds(bookIds, today)
                .stream()
                .collect(Collectors.toMap(
                        editorialPrice -> editorialPrice.getBook().getId(),
                        Function.identity()
                ));
    }
}
