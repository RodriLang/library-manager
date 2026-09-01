package com.rodrilang.librarymanager.service.impl;

import com.rodrilang.librarymanager.dto.internal.InventoryEditorialPriceSyncResult;
import com.rodrilang.librarymanager.editorialprice.dto.internal.EffectiveEditorialPriceRefreshResult;
import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceOrigin;
import com.rodrilang.librarymanager.editorialprice.service.EditorialPriceHealthCacheService;
import com.rodrilang.librarymanager.editorialprice.service.EffectiveEditorialPriceService;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.internal.EditorialPriceInsertRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.EditorialPriceUpdateRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceImportCounters;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListImportItemInsertRow;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListResolvedPrice;
import com.rodrilang.librarymanager.importer.price.enums.EditorialPriceChange;
import com.rodrilang.librarymanager.importer.price.enums.PriceListImportItemOperation;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.repository.EditorialPriceBatchRepository;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportItemBatchRepository;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubePriceSyncRequestedEvent;
import com.rodrilang.librarymanager.repository.EditorialPriceRepository;
import com.rodrilang.librarymanager.repository.InventoryEditorialPriceSyncRepository;
import com.rodrilang.librarymanager.repository.projection.EditorialPriceImportProjection;
import com.rodrilang.librarymanager.repository.projection.PreviousEditorialPriceProjection;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final PriceListImportItemBatchRepository importItemBatchRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EffectiveEditorialPriceService effectiveEditorialPriceService;
    private final EditorialPriceHealthCacheService editorialPriceHealthCacheService;

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

        List<EditorialPriceImportProjection> existingPrices = editorialPriceRepository.findForImport(
                bookIds,
                job.getProvider().getId(),
                job.getValidFrom(),
                EditorialPriceOrigin.PRICE_LIST
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

        Map<Long, PriceListImportItemOperation> operationByBookId = new HashMap<>();

        Map<Long, BigDecimal> previousPriceByBookId =
                editorialPriceRepository
                        .findLatestBeforeImport(
                                job.getProvider().getId(),
                                bookIds,
                                job.getValidFrom()
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        PreviousEditorialPriceProjection::getBookId,
                                        PreviousEditorialPriceProjection::getPrice
                                )
                        );

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

                operationByBookId.put(
                        resolved.bookId(),
                        PriceListImportItemOperation.CREATED
                );

                continue;
            }

            if (
                    existing.getPrice().compareTo(resolved.selectedPrice()) == 0
                            && Boolean.TRUE.equals(existing.getActive())
            ) {
                unchanged++;

                operationByBookId.put(
                        resolved.bookId(),
                        PriceListImportItemOperation.UNCHANGED
                );

                continue;
            }

            toUpdate.add(
                    new EditorialPriceUpdateRow(
                            existing.getId(),
                            resolved.selectedPrice()
                    )
            );

            operationByBookId.put(
                    resolved.bookId(),
                    PriceListImportItemOperation.UPDATED
            );
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

        Map<Long, EditorialPriceImportProjection> persistedByBookId = editorialPriceRepository
                .findForImport(bookIds, job.getProvider().getId(), job.getValidFrom(), EditorialPriceOrigin.PRICE_LIST)
                .stream()
                .collect(Collectors.toMap(EditorialPriceImportProjection::getBookId, Function.identity()));

        List<PriceListImportItemInsertRow> importItems =
                prices.stream()
                        .map(resolved -> {

                            EditorialPriceImportProjection persistedPrice =
                                    persistedByBookId.get(
                                            resolved.bookId()
                                    );

                            if (persistedPrice == null) {
                                throw new BusinessException(
                                        "No se pudo recuperar el precio editorial "
                                                + "del libro "
                                                + resolved.bookId()
                                                + " después de la importación."
                                );
                            }

                            BigDecimal previousPrice =
                                    previousPriceByBookId.get(
                                            resolved.bookId()
                                    );

                            PriceListImportItemOperation operation =
                                    operationByBookId.get(
                                            resolved.bookId()
                                    );

                            if (operation == null) {
                                throw new BusinessException(
                                        "No se pudo determinar el resultado "
                                                + "de importación del libro "
                                                + resolved.bookId()
                                                + "."
                                );
                            }

                            return new PriceListImportItemInsertRow(
                                    resolved.bookId(),
                                    persistedPrice.getId(),
                                    resolved.selectedPrice(),
                                    previousPrice,
                                    operation,
                                    classifyPriceChange(
                                            previousPrice,
                                            resolved.selectedPrice()
                                    )
                            );
                        })
                        .toList();

        importItemBatchRepository.insertBatch(
                jobId,
                importItems
        );

        EffectiveEditorialPriceRefreshResult effectiveRefresh =
                effectiveEditorialPriceService.refreshForBooks(
                        bookIds,
                        job.getValidFrom()
                );

        InventoryEditorialPriceSyncResult inventorySyncResult =
                inventoryEditorialPriceSyncRepository.syncCurrentPrices(
                        effectiveRefresh.changedBookIds(),
                        LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"))
                );

        log.info(
                "Resolved price inventory sync completed. "
                        + "jobId={} effectiveChangedBooks={} "
                        + "conflictedBooks={} "
                        + "updatedInventories={} "
                        + "tiendanubeSyncRequested={}",
                jobId,
                effectiveRefresh.changedBookIds().size(),
                effectiveRefresh.conflictedBookIds().size(),
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

        editorialPriceHealthCacheService.evictSummaryAfterCommit();

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

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> findCurrentPricesByBookIds(List<Long> bookIds) {
        return effectiveEditorialPriceService.findCurrentByBookIds(bookIds)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getPrice()));
    }

    private EditorialPriceChange classifyPriceChange(
            BigDecimal previousPrice,
            BigDecimal importedPrice
    ) {
        if (previousPrice == null) {
            return EditorialPriceChange.FIRST_PRICE;
        }

        int comparison = importedPrice.compareTo(previousPrice);

        if (comparison > 0) {
            return EditorialPriceChange.INCREASED;
        }

        if (comparison < 0) {
            return EditorialPriceChange.DECREASED;
        }

        return EditorialPriceChange.UNCHANGED;
    }
}
