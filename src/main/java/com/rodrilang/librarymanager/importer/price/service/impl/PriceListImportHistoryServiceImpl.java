package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.enums.RowValidationSeverity;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportDetailResponse;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportHistoryItemResponse;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportItemResponse;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportJobErrorResponse;
import com.rodrilang.librarymanager.importer.price.enums.EditorialPriceChange;
import com.rodrilang.librarymanager.importer.price.enums.PriceListImportItemOperation;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportItem;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJob;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobStatus;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportItemRepository;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobErrorRepository;
import com.rodrilang.librarymanager.importer.price.repository.PriceListImportJobRepository;
import com.rodrilang.librarymanager.importer.price.repository.projection.PriceListImportPriceChangeCountProjection;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportHistoryService;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceListImportHistoryServiceImpl implements PriceListImportHistoryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final PriceListImportJobRepository jobRepository;
    private final PriceListImportItemRepository itemRepository;
    private final PriceListImportJobErrorRepository errorRepository;

    @Override
    public Page<PriceListImportHistoryItemResponse> findImports(
            Long providerId,
            PriceListImportJobStatus status,
            LocalDate validFromFrom,
            LocalDate validFromTo,
            LocalDate createdFrom,
            LocalDate createdTo,
            Pageable pageable
    ) {
        Specification<PriceListImportJob> specification =
                buildJobSpecification(
                        providerId,
                        status,
                        validFromFrom,
                        validFromTo,
                        createdFrom,
                        createdTo
                );

        Page<PriceListImportJob> jobs =
                jobRepository.findAll(
                        specification,
                        pageable
                );

        if (jobs.isEmpty()) {
            return jobs.map(
                    job -> toHistoryResponse(
                            job,
                            PriceChangeSummary.empty()
                    )
            );
        }

        List<Long> jobIds = jobs
                .getContent()
                .stream()
                .map(PriceListImportJob::getId)
                .toList();

        Map<Long, PriceChangeSummary> summaries =
                loadPriceChangeSummaries(jobIds);

        return jobs.map(
                job -> toHistoryResponse(
                        job,
                        summaries.getOrDefault(
                                job.getId(),
                                PriceChangeSummary.empty()
                        )
                )
        );
    }

    @Override
    public PriceListImportDetailResponse findImport(
            Long jobId
    ) {
        PriceListImportJob job = findJob(jobId);

        PriceChangeSummary summary =
                loadPriceChangeSummaries(
                        List.of(jobId)
                )
                        .getOrDefault(
                                jobId,
                                PriceChangeSummary.empty()
                        );

        boolean itemHistoryAvailable =
                itemRepository.existsByJobId(jobId);

        return new PriceListImportDetailResponse(
                job.getId(),

                job.getProvider().getId(),
                job.getProvider().getName(),

                job.getOriginalFileName(),

                job.getValidFrom(),

                job.getStatus(),
                job.getPhase(),

                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),

                job.getTotalRows(),
                job.getProcessedRows(),
                job.getProcessedBooks(),
                job.getDuplicateBookRows(),
                job.getProcessedPrices(),

                job.getCreatedBooks(),

                job.getCreatedPrices(),
                job.getUpdatedPrices(),
                job.getUnchangedPrices(),

                job.getSkippedRows(),

                job.getErrorCount(),
                job.getErrorMessage(),

                summary.firstPrices(),
                summary.increasedPrices(),
                summary.decreasedPrices(),
                summary.maintainedPrices(),

                itemHistoryAvailable
        );
    }

    @Override
    public Page<PriceListImportItemResponse> findImportItems(
            Long jobId,
            EditorialPriceChange priceChange,
            PriceListImportItemOperation operation,
            String query,
            Pageable pageable
    ) {
        ensureJobExists(jobId);

        String normalizedQuery = normalizeQuery(query);

        Page<Long> itemIds =
                itemRepository.findHistoryItemIds(
                        jobId,
                        priceChange,
                        operation,
                        normalizedQuery,
                        pageable
                );

        if (itemIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<PriceListImportItem> items = itemRepository.findHistoryItemsByIds(itemIds.getContent());

        Map<Long, PriceListImportItem> itemById =
                items.stream()
                        .collect(
                                Collectors.toMap(
                                        PriceListImportItem::getId,
                                        Function.identity()
                                )
                        );

        List<PriceListImportItemResponse> content =
                itemIds.getContent()
                        .stream()
                        .map(itemById::get)
                        .filter(Objects::nonNull)
                        .map(this::toItemResponse)
                        .toList();

        return new PageImpl<>(
                content,
                pageable,
                itemIds.getTotalElements()
        );
    }

    @Override
    public Page<PriceListImportJobErrorResponse> findImportErrors(
            Long jobId,
            RowValidationSeverity severity,
            Pageable pageable
    ) {
        ensureJobExists(jobId);

        return errorRepository
                .findHistoryErrors(
                        jobId,
                        severity,
                        pageable
                )
                .map(error ->
                        new PriceListImportJobErrorResponse(
                                error.getRowNumber(),
                                error.getIsbn(),
                                error.getMessage(),
                                error.getSeverity()
                        )
                );
    }

    private Specification<PriceListImportJob> buildJobSpecification(
            Long providerId,
            PriceListImportJobStatus status,
            LocalDate validFromFrom,
            LocalDate validFromTo,
            LocalDate createdFrom,
            LocalDate createdTo
    ) {
        ZoneId zoneId = ZoneId.systemDefault();

        Instant createdFromInstant =
                createdFrom == null
                        ? null
                        : createdFrom
                        .atStartOfDay(zoneId)
                        .toInstant();

        Instant createdToExclusive =
                createdTo == null
                        ? null
                        : createdTo
                        .plusDays(1)
                        .atStartOfDay(BUSINESS_ZONE)
                        .toInstant();

        return (root, query, criteriaBuilder) -> {

            List<Predicate> predicates =
                    new ArrayList<>();

            if (providerId != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("provider").get("id"),
                                providerId
                        )
                );
            }

            if (status != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("status"),
                                status
                        )
                );
            }

            if (validFromFrom != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("validFrom"),
                                validFromFrom
                        )
                );
            }

            if (validFromTo != null) {
                predicates.add(
                        criteriaBuilder.lessThanOrEqualTo(
                                root.get("validFrom"),
                                validFromTo
                        )
                );
            }

            if (createdFromInstant != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("createdAt"),
                                createdFromInstant
                        )
                );
            }

            if (createdToExclusive != null) {
                predicates.add(
                        criteriaBuilder.lessThan(
                                root.get("createdAt"),
                                createdToExclusive
                        )
                );
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Map<Long, PriceChangeSummary> loadPriceChangeSummaries(Collection<Long> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, EnumMap<EditorialPriceChange, Integer>> counters = new HashMap<>();

        List<PriceListImportPriceChangeCountProjection> rows = itemRepository.countPriceChangesByJobIds(jobIds);

        for (PriceListImportPriceChangeCountProjection row : rows) {

            EnumMap<EditorialPriceChange, Integer> jobCounters =
                    counters.computeIfAbsent(
                            row.getJobId(),
                            ignored ->
                                    new EnumMap<>(
                                            EditorialPriceChange.class
                                    )
                    );

            jobCounters.put(
                    row.getPriceChange(),
                    Math.toIntExact(row.getTotal())
            );
        }

        Map<Long, PriceChangeSummary> result =
                new HashMap<>();

        counters.forEach(
                (jobId, values) ->
                        result.put(
                                jobId,
                                new PriceChangeSummary(
                                        values.getOrDefault(
                                                EditorialPriceChange.FIRST_PRICE,
                                                0
                                        ),
                                        values.getOrDefault(
                                                EditorialPriceChange.INCREASED,
                                                0
                                        ),
                                        values.getOrDefault(
                                                EditorialPriceChange.DECREASED,
                                                0
                                        ),
                                        values.getOrDefault(
                                                EditorialPriceChange.UNCHANGED,
                                                0
                                        )
                                )
                        )
        );

        return result;
    }

    private PriceListImportHistoryItemResponse toHistoryResponse(
            PriceListImportJob job,
            PriceChangeSummary summary
    ) {
        return new PriceListImportHistoryItemResponse(
                job.getId(),

                job.getProvider().getId(),
                job.getProvider().getName(),

                job.getOriginalFileName(),

                job.getStatus(),
                job.getValidFrom(),

                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),

                job.getTotalRows(),
                job.getProcessedBooks(),

                job.getCreatedBooks(),

                job.getCreatedPrices(),
                job.getUpdatedPrices(),
                job.getUnchangedPrices(),

                job.getSkippedRows(),
                job.getErrorCount(),

                summary.firstPrices(),
                summary.increasedPrices(),
                summary.decreasedPrices(),
                summary.maintainedPrices()
        );
    }

    private PriceListImportItemResponse toItemResponse(PriceListImportItem item) {
        Book book = item.getBook();

        return new PriceListImportItemResponse(
                item.getId(),

                book.getId(),

                book.getPreferredIsbn(),
                book.getTitle(),

                book.getAuthors()
                        .stream()
                        .map(Author::getName)
                        .sorted()
                        .toList(),

                book.getPublisher() != null
                        ? book.getPublisher().getName()
                        : null,

                item.getEditorialPrice().getId(),

                item.getPreviousPrice(),
                item.getImportedPrice(),

                calculateChangePercentage(
                        item.getPreviousPrice(),
                        item.getImportedPrice()
                ),

                item.getOperation(),
                item.getPriceChange()
        );
    }

    private BigDecimal calculateChangePercentage(
            BigDecimal previousPrice,
            BigDecimal importedPrice
    ) {
        if (previousPrice == null || importedPrice == null || previousPrice.signum() == 0) {
            return null;
        }

        return importedPrice
                .subtract(previousPrice)
                .divide(previousPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private PriceListImportJob findJob(Long jobId) {
        return jobRepository.findByIdWithProvider(jobId)
                .orElseThrow(() -> new BusinessException("No se encontró la importación solicitada."));
    }

    private void ensureJobExists(Long jobId) {
        if (!jobRepository.existsById(jobId)) {
            throw new BusinessException("No se encontró la importación solicitada.");
        }
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }

        return query
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private record PriceChangeSummary(
            int firstPrices,
            int increasedPrices,
            int decreasedPrices,
            int maintainedPrices
    ) {

        private static PriceChangeSummary empty() {
            return new PriceChangeSummary(
                    0,
                    0,
                    0,
                    0
            );
        }
    }
}