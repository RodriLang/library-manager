package com.rodrilang.librarymanager.editorialprice.service.impl;

import com.rodrilang.librarymanager.editorialprice.dto.internal.EffectiveEditorialPriceInsertRow;
import com.rodrilang.librarymanager.editorialprice.dto.internal.EffectiveEditorialPriceRefreshResult;
import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceAuthority;
import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceOrigin;
import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceResolutionType;
import com.rodrilang.librarymanager.editorialprice.enums.EffectiveEditorialPriceDeterminationType;
import com.rodrilang.librarymanager.editorialprice.enums.EffectiveEditorialPriceInvalidationReason;
import com.rodrilang.librarymanager.editorialprice.model.EditorialPriceResolution;
import com.rodrilang.librarymanager.editorialprice.model.EffectiveEditorialPrice;
import com.rodrilang.librarymanager.editorialprice.repository.EditorialPriceResolutionRepository;
import com.rodrilang.librarymanager.editorialprice.repository.EffectiveEditorialPriceBatchRepository;
import com.rodrilang.librarymanager.editorialprice.repository.EffectiveEditorialPriceRepository;
import com.rodrilang.librarymanager.editorialprice.service.EffectiveEditorialPriceService;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.EditorialPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EffectiveEditorialPriceServiceImpl implements EffectiveEditorialPriceService {

    private static final ZoneId ANAQUEL_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final Set<EditorialPriceOrigin> OFFICIAL_ORIGINS =
            EnumSet.of(
                    EditorialPriceOrigin.PRICE_LIST,
                    EditorialPriceOrigin.MANUAL_DISTRIBUTOR,
                    EditorialPriceOrigin.MANUAL_PUBLISHER
            );

    private final BookRepository bookRepository;
    private final EditorialPriceRepository editorialPriceRepository;
    private final EffectiveEditorialPriceRepository effectivePriceRepository;
    private final EffectiveEditorialPriceBatchRepository effectivePriceBatchRepository;
    private final EditorialPriceResolutionRepository resolutionRepository;

    @Override
    @Transactional
    public EffectiveEditorialPriceRefreshResult refreshForBooks(
            Collection<Long> requestedBookIds,
            LocalDate affectedValidFrom
    ) {
        if (requestedBookIds == null || requestedBookIds.isEmpty()) {
            return emptyResult();
        }

        if (affectedValidFrom == null) {
            throw new IllegalArgumentException("La fecha de vigencia afectada es obligatoria.");
        }

        long totalStartedAt = System.nanoTime();

        List<Long> bookIds = requestedBookIds
                .stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .sorted()
                .toList();

        if (bookIds.isEmpty()) {
            return emptyResult();
        }

        log.info(
                "Effective refresh started. books={} from={}",
                bookIds.size(),
                affectedValidFrom
        );

        /*
         * Serializa el resolver por libro.
         * Dos imports de providers diferentes pueden afectar
         * exactamente el mismo libro.
         */
        long lockStartedAt = System.nanoTime();

        bookRepository.lockByIds(bookIds);

        log.info(
                "Effective refresh books locked. books={} time={}ms",
                bookIds.size(),
                elapsedMs(lockStartedAt)
        );

        LocalDate today = LocalDate.now(ANAQUEL_ZONE);

        long currentBeforeStartedAt = System.nanoTime();

        Map<Long, PriceSnapshot> currentBefore =
                toCurrentSnapshotMap(
                        effectivePriceRepository.findCurrentByBookIds(
                                bookIds,
                                today
                        )
                );

        log.info(
                "Effective refresh current-before loaded. "
                        + "books={} currentPrices={} time={}ms",
                bookIds.size(),
                currentBefore.size(),
                elapsedMs(currentBeforeStartedAt)
        );

        long baselineStartedAt = System.nanoTime();

        Map<Long, EffectiveEditorialPrice> baselineByBookId =
                effectivePriceRepository
                        .findLatestBefore(
                                bookIds,
                                affectedValidFrom
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        price -> price.getBook().getId(),
                                        Function.identity()
                                )
                        );

        log.info(
                "Effective refresh baselines loaded. "
                        + "books={} baselines={} time={}ms",
                bookIds.size(),
                baselineByBookId.size(),
                elapsedMs(baselineStartedAt)
        );

        long sourcePricesStartedAt = System.nanoTime();

        List<EditorialPrice> sourcePrices = editorialPriceRepository.findActiveFrom(bookIds, affectedValidFrom);

        log.info(
                "Effective refresh source prices loaded. "
                        + "books={} sourcePrices={} time={}ms",
                bookIds.size(),
                sourcePrices.size(),
                elapsedMs(sourcePricesStartedAt)
        );

        long officialBeforeStartedAt = System.nanoTime();

        Set<Long> booksWithOfficialBefore =
                new LinkedHashSet<>(
                        editorialPriceRepository
                                .findBookIdsWithOfficialPriceBefore(
                                        bookIds,
                                        affectedValidFrom,
                                        OFFICIAL_ORIGINS
                                )
                );

        log.info(
                "Effective refresh official-before loaded. "
                        + "books={} booksWithOfficialBefore={} time={}ms",
                bookIds.size(),
                booksWithOfficialBefore.size(),
                elapsedMs(officialBeforeStartedAt)
        );

        long resolutionsStartedAt = System.nanoTime();

        List<EditorialPriceResolution> resolutions = resolutionRepository.findActiveFrom(bookIds, affectedValidFrom);

        log.info(
                "Effective refresh resolutions loaded. "
                        + "books={} resolutions={} time={}ms",
                bookIds.size(),
                resolutions.size(),
                elapsedMs(resolutionsStartedAt)
        );

        long groupingStartedAt = System.nanoTime();

        Map<Long, Map<LocalDate, List<EditorialPrice>>> sourcesByBook = groupSources(sourcePrices);

        Map<Long, Map<LocalDate, EditorialPriceResolution>> resolutionsByBook = groupResolutions(resolutions);

        log.info(
                "Effective refresh data grouped. "
                        + "sourceBooks={} resolutionBooks={} time={}ms",
                sourcesByBook.size(),
                resolutionsByBook.size(),
                elapsedMs(groupingStartedAt)
        );

        /*
         * Recalculamos el sufijo temporal completo desde la fecha
         * afectada. Esto también soporta correcciones históricas.
         */
        long invalidateStartedAt = System.nanoTime();

        effectivePriceRepository.invalidateFrom(
                bookIds,
                affectedValidFrom,
                Instant.now(),
                EffectiveEditorialPriceInvalidationReason.SUPERSEDED_CORRECTION
        );

        log.info(
                "Effective refresh previous rows invalidated. "
                        + "books={} from={} time={}ms",
                bookIds.size(),
                affectedValidFrom,
                elapsedMs(invalidateStartedAt)
        );

        List<EffectiveEditorialPriceInsertRow> toInsert = new ArrayList<>();

        Set<Long> conflictedBookIds = new LinkedHashSet<>();

        long resolveStartedAt = System.nanoTime();

        int evaluatedDates = 0;
        int candidateCount = 0;
        int skippedSamePrice = 0;
        int skippedWithoutCandidate = 0;

        for (Long bookId : bookIds) {
            EffectiveEditorialPrice baseline = baselineByBookId.get(bookId);

            RunningPrice runningPrice =
                    baseline == null
                            ? null
                            : new RunningPrice(
                            baseline.getPrice(),
                            baseline.getCurrency()
                    );

            boolean officialSeen = booksWithOfficialBefore.contains(bookId);

            Map<LocalDate, List<EditorialPrice>> sourcesByDate = sourcesByBook.getOrDefault(bookId, Map.of());

            Map<LocalDate, EditorialPriceResolution> resolutionsByDate = resolutionsByBook.getOrDefault(bookId, Map.of());

            TreeSet<LocalDate> dates = new TreeSet<>();

            dates.addAll(sourcesByDate.keySet());
            dates.addAll(resolutionsByDate.keySet());

            for (LocalDate validFrom : dates) {
                evaluatedDates++;

                List<EditorialPrice> sources = sourcesByDate.getOrDefault(validFrom, List.of());

                List<EditorialPrice> officialSources = sources.stream()
                        .filter(this::isOfficial)
                        .toList();

                /*
                 * Desde que alguna fuente oficial existe, una
                 * referencia externa nunca reemplaza automáticamente
                 * el precio oficial.
                 */
                if (!officialSources.isEmpty()) {
                    officialSeen = true;
                }

                EditorialPriceResolution resolution = resolutionsByDate.get(validFrom);

                ResolutionResult resolutionResult =
                        resolveCandidate(
                                sources,
                                officialSources,
                                resolution,
                                officialSeen
                        );

                if (resolutionResult.officialConflict()) {
                    conflictedBookIds.add(bookId);
                }

                PriceCandidate candidate =
                        resolutionResult.candidate();

                if (candidate == null) {
                    /*
                     * No adoptamos nada nuevo.
                     * El precio efectivo anterior continúa vigente.
                     */
                    skippedWithoutCandidate++;
                    continue;
                }

                candidateCount++;

                if (
                        runningPrice != null
                                && samePrice(
                                runningPrice.price(),
                                runningPrice.currency(),
                                candidate.price(),
                                candidate.currency()
                        )
                ) {
                    /*
                     * OPCIÓN A:
                     * mismo importe efectivo => no insertamos otra fila.
                     */
                    skippedSamePrice++;
                    continue;
                }

                Long selectedEditorialPriceId =
                        candidate.selectedSource() != null
                                ? candidate.selectedSource().getId()
                                : null;

                Long resolutionId =
                        candidate.resolution() != null
                                ? candidate.resolution().getId()
                                : null;

                toInsert.add(
                        new EffectiveEditorialPriceInsertRow(
                                bookId,
                                candidate.price(),
                                candidate.currency(),
                                validFrom,
                                candidate.determinationType(),
                                candidate.authority(),
                                selectedEditorialPriceId,
                                resolutionId
                        )
                );

                runningPrice = new RunningPrice(candidate.price(), candidate.currency());
            }
        }

        log.info(
                "Effective refresh resolution completed. "
                        + "books={} evaluatedDates={} candidates={} "
                        + "toInsert={} skippedSamePrice={} "
                        + "skippedWithoutCandidate={} conflicts={} time={}ms",
                bookIds.size(),
                evaluatedDates,
                candidateCount,
                toInsert.size(),
                skippedSamePrice,
                skippedWithoutCandidate,
                conflictedBookIds.size(),
                elapsedMs(resolveStartedAt)
        );

        long persistenceStartedAt = System.nanoTime();

        effectivePriceBatchRepository.insertBatch(toInsert);

        log.info(
                "Effective refresh persistence completed. rows={} time={}ms",
                toInsert.size(),
                elapsedMs(persistenceStartedAt)
        );

        long currentAfterStartedAt = System.nanoTime();

        Map<Long, PriceSnapshot> currentAfter =
                toCurrentSnapshotMap(effectivePriceRepository.findCurrentByBookIds(bookIds, today));

        log.info(
                "Effective refresh current-after loaded. "
                        + "books={} currentPrices={} time={}ms",
                bookIds.size(),
                currentAfter.size(),
                elapsedMs(currentAfterStartedAt)
        );

        long comparisonStartedAt = System.nanoTime();

        Set<Long> changedBookIds = new LinkedHashSet<>();

        for (Long bookId : bookIds) {
            if (!sameSnapshot(currentBefore.get(bookId), currentAfter.get(bookId))) {
                changedBookIds.add(bookId);
            }
        }

        log.info(
                "Effective refresh current comparison completed. "
                        + "books={} changedCurrent={} time={}ms",
                bookIds.size(),
                changedBookIds.size(),
                elapsedMs(comparisonStartedAt)
        );

        log.info(
                "Effective editorial prices refreshed. "
                        + "books={} from={} sourcePrices={} "
                        + "inserted={} changedCurrent={} conflicts={} "
                        + "totalTime={}ms",
                bookIds.size(),
                affectedValidFrom,
                sourcePrices.size(),
                toInsert.size(),
                changedBookIds.size(),
                conflictedBookIds.size(),
                elapsedMs(totalStartedAt)
        );

        return new EffectiveEditorialPriceRefreshResult(
                Set.copyOf(changedBookIds),
                Set.copyOf(conflictedBookIds)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EffectiveEditorialPrice> findCurrentByBookId(Long bookId) {
        return effectivePriceRepository
                .findFirstByBookIdAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDescIdDesc(
                        bookId,
                        LocalDate.now(ANAQUEL_ZONE)
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, EffectiveEditorialPrice> findCurrentByBookIds(Collection<Long> requestedBookIds) {
        if (requestedBookIds == null || requestedBookIds.isEmpty()) {
            return Map.of();
        }

        List<Long> bookIds = requestedBookIds
                .stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        if (bookIds.isEmpty()) {
            return Map.of();
        }

        return effectivePriceRepository
                .findCurrentByBookIds(bookIds, LocalDate.now(ANAQUEL_ZONE))
                .stream()
                .collect(
                        Collectors.toMap(
                                price -> price.getBook().getId(),
                                Function.identity()
                        )
                );
    }

    private ResolutionResult resolveCandidate(
            List<EditorialPrice> allSources,
            List<EditorialPrice> officialSources,
            EditorialPriceResolution resolution,
            boolean officialSeen
    ) {
        /*
         * Una resolución humana siempre prevalece para ESTA vigencia.
         */
        if (resolution != null) {
            EditorialPrice selected =
                    resolution.getSelectedEditorialPrice();

            EffectiveEditorialPriceDeterminationType type =
                    resolution.getResolutionType() == EditorialPriceResolutionType.SOURCE_SELECTION
                            ? EffectiveEditorialPriceDeterminationType.MANUAL_SOURCE_SELECTION
                            : EffectiveEditorialPriceDeterminationType.MANUAL_OVERRIDE;

            EditorialPriceAuthority authority = isOfficial(selected)
                    ? EditorialPriceAuthority.OFFICIAL
                    : EditorialPriceAuthority.EXTERNAL_REFERENCE;

            return new ResolutionResult(
                    new PriceCandidate(
                            resolution.getResolvedPrice(),
                            normalizeCurrency(selected.getCurrency()),
                            type,
                            authority,
                            selected,
                            resolution
                    ),
                    hasOfficialConflict(officialSources)
            );
        }

        /*
         * Hay fuentes oficiales para esta misma fecha.
         */
        if (!officialSources.isEmpty()) {
            Map<PriceKey, List<EditorialPrice>> distinctValues = groupByValue(officialSources);

            if (distinctValues.size() > 1) {

                return new ResolutionResult(null, true);
            }

            List<EditorialPrice> agreeingSources =
                    distinctValues
                            .values()
                            .iterator()
                            .next();

            EditorialPrice first = agreeingSources.getFirst();

            EffectiveEditorialPriceDeterminationType type = agreeingSources.size() == 1
                    ? EffectiveEditorialPriceDeterminationType.AUTO_SINGLE_SOURCE
                    : EffectiveEditorialPriceDeterminationType.AUTO_SOURCE_AGREEMENT;

            return new ResolutionResult(
                    new PriceCandidate(
                            first.getPrice(),
                            normalizeCurrency(first.getCurrency()),
                            type,
                            EditorialPriceAuthority.OFFICIAL,
                            agreeingSources.size() == 1
                                    ? first
                                    : null,
                            null
                    ),
                    false
            );
        }

        /*
         * Si alguna vez ya tuvimos una fuente oficial, las referencias
         * externas quedan como evidencia pero no reemplazan
         * automáticamente ese precio.
         */
        if (officialSeen) {
            return new ResolutionResult(null, false);
        }

        List<EditorialPrice> externalSources = allSources.stream()
                .filter(price -> price.getOrigin() == EditorialPriceOrigin.MANUAL_EXTERNAL)
                .toList();

        if (externalSources.isEmpty()) {
            return new ResolutionResult(null, false);
        }

        Map<PriceKey, List<EditorialPrice>> distinctValues = groupByValue(externalSources);

        /*
         * Dos referencias externas con valores diferentes tampoco
         * deben desempatarse automáticamente.
         */
        if (distinctValues.size() > 1) {
            return new ResolutionResult(null, false);
        }

        List<EditorialPrice> agreeingSources =
                distinctValues
                        .values()
                        .iterator()
                        .next();

        EditorialPrice first = agreeingSources.getFirst();

        EffectiveEditorialPriceDeterminationType type = agreeingSources.size() == 1
                ? EffectiveEditorialPriceDeterminationType.AUTO_SINGLE_SOURCE
                : EffectiveEditorialPriceDeterminationType.AUTO_SOURCE_AGREEMENT;

        return new ResolutionResult(
                new PriceCandidate(
                        first.getPrice(),
                        normalizeCurrency(first.getCurrency()),
                        type,
                        EditorialPriceAuthority.EXTERNAL_REFERENCE,
                        agreeingSources.size() == 1
                                ? first
                                : null,
                        null
                ),
                false
        );
    }

    private boolean hasOfficialConflict(List<EditorialPrice> officialSources) {
        if (officialSources.size() < 2) {
            return false;
        }

        return groupByValue(officialSources).size() > 1;
    }

    private Map<PriceKey, List<EditorialPrice>> groupByValue(List<EditorialPrice> prices) {
        return prices.stream()
                .collect(
                        Collectors.groupingBy(
                                price -> new PriceKey(
                                        normalizePrice(price.getPrice()),
                                        normalizeCurrency(price.getCurrency())
                                )
                        )
                );
    }

    private Map<Long, Map<LocalDate, List<EditorialPrice>>> groupSources(
            List<EditorialPrice> prices
    ) {
        return prices.stream()
                .collect(
                        Collectors.groupingBy(
                                price -> price
                                        .getBook()
                                        .getId(),
                                Collectors.groupingBy(
                                        EditorialPrice::getValidFrom
                                )
                        )
                );
    }

    private Map<Long, Map<LocalDate, EditorialPriceResolution>> groupResolutions(
            List<EditorialPriceResolution> resolutions
    ) {
        Map<Long, Map<LocalDate, EditorialPriceResolution>> result = new HashMap<>();

        for (EditorialPriceResolution resolution : resolutions) {
            result.computeIfAbsent(
                            resolution.getBook().getId(),
                            ignored -> new HashMap<>()
                    )
                    .put(
                            resolution.getValidFrom(),
                            resolution
                    );
        }

        return result;
    }

    private Map<Long, PriceSnapshot> toCurrentSnapshotMap(List<EffectiveEditorialPrice> prices) {
        return prices.stream()
                .collect(
                        Collectors.toMap(
                                price -> price.getBook().getId(),
                                price -> new PriceSnapshot(
                                        price.getPrice(),
                                        price.getCurrency()
                                )
                        )
                );
    }

    private boolean isOfficial(EditorialPrice price) {
        return price != null && OFFICIAL_ORIGINS.contains(price.getOrigin());
    }

    private boolean sameSnapshot(PriceSnapshot first, PriceSnapshot second) {
        if (first == null || second == null) {
            return first == second;
        }

        return samePrice(
                first.price(),
                first.currency(),
                second.price(),
                second.currency()
        );
    }

    private boolean samePrice(
            BigDecimal firstPrice,
            String firstCurrency,
            BigDecimal secondPrice,
            String secondCurrency
    ) {
        return firstPrice.compareTo(secondPrice) == 0 && normalizeCurrency(firstCurrency)
                .equals(normalizeCurrency(secondCurrency));
    }

    private BigDecimal normalizePrice(BigDecimal value) {
        return value.stripTrailingZeros();
    }

    private String normalizeCurrency(String currency) {
        return currency == null
                ? "ARS"
                : currency
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private EffectiveEditorialPriceRefreshResult emptyResult() {
        return new EffectiveEditorialPriceRefreshResult(
                Set.of(),
                Set.of()
        );
    }

    private record PriceKey(
            BigDecimal price,
            String currency
    ) {
    }

    private record RunningPrice(
            BigDecimal price,
            String currency
    ) {
    }

    private record PriceSnapshot(
            BigDecimal price,
            String currency
    ) {
    }

    private record PriceCandidate(
            BigDecimal price,
            String currency,
            EffectiveEditorialPriceDeterminationType determinationType,
            EditorialPriceAuthority authority,
            EditorialPrice selectedSource,
            EditorialPriceResolution resolution
    ) {
    }

    private record ResolutionResult(
            PriceCandidate candidate,
            boolean officialConflict
    ) {
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}