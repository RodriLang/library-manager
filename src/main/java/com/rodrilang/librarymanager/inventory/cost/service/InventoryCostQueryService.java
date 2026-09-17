package com.rodrilang.librarymanager.inventory.cost.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.inventory.cost.dto.response.InventoryCostLayerResponse;
import com.rodrilang.librarymanager.inventory.cost.dto.response.InventoryCostSummaryResponse;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostLayer;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostType;
import com.rodrilang.librarymanager.inventory.cost.repository.InventoryCostLayerRepository;
import com.rodrilang.librarymanager.inventory.cost.repository.InventoryCostSummaryRepository;
import com.rodrilang.librarymanager.inventory.cost.repository.projection.InventoryCostSummaryProjection;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InventoryCostQueryService {

    private final InventoryCostLayerRepository layerRepository;
    private final InventoryCostSummaryRepository summaryRepository;
    private final InventoryCostResponseMapper responseMapper;
    private final BookstoreContext bookstoreContext;

    @Transactional(readOnly = true)
    public Page<InventoryCostLayerResponse> findAll(
            InventoryCostType costType,
            Boolean missingDiscount,
            Boolean remainingOnly,
            String search,
            Pageable pageable
    ) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Specification<InventoryCostLayer> specification = bookstore(bookstoreId)
                .and(notReversed())
                .and(costType(costType))
                .and(missingDiscount(missingDiscount))
                .and(remainingOnly(remainingOnly))
                .and(search(search));

        return layerRepository.findAll(specification, pageable).map(responseMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public InventoryCostSummaryResponse summary() {
        InventoryCostSummaryProjection summary = summaryRepository.summarize(bookstoreContext.getCurrentBookstoreId());
        long currentUnits = value(summary.getCurrentUnits());
        long knownUnits = value(summary.getKnownCostUnits());

        BigDecimal coverage = currentUnits == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(knownUnits)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(currentUnits), 2, RoundingMode.HALF_UP);

        return new InventoryCostSummaryResponse(
                currentUnits,
                knownUnits,
                value(summary.getUnknownCostUnits()),
                value(summary.getRealCostUnits()),
                value(summary.getEstimatedCostUnits()),
                value(summary.getUnknownCostLayers()),
                value(summary.getMissingDiscountLayers()),
                coverage
        );
    }

    private Specification<InventoryCostLayer> bookstore(Long bookstoreId) {
        return (root, query, cb) -> cb.equal(root.get("inventory").get("bookstore").get("id"), bookstoreId);
    }

    private Specification<InventoryCostLayer> notReversed() {
        return (root, query, cb) -> cb.isNull(root.get("reversedAt"));
    }

    private Specification<InventoryCostLayer> costType(InventoryCostType costType) {
        return costType == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("costType"), costType);
    }

    private Specification<InventoryCostLayer> missingDiscount(Boolean missingDiscount) {
        if (missingDiscount == null) {
            return null;
        }

        return missingDiscount
                ? (root, query, cb) -> cb.isNull(root.get("discountPercentage"))
                : (root, query, cb) -> cb.isNotNull(root.get("discountPercentage"));
    }

    private Specification<InventoryCostLayer> remainingOnly(Boolean remainingOnly) {
        if (!Boolean.TRUE.equals(remainingOnly)) {
            return null;
        }

        return (root, query, cb) -> cb.greaterThan(root.get("quantityRemaining"), 0);
    }

    private Specification<InventoryCostLayer> search(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        String value = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> {
            var inventory = root.join("inventory", JoinType.INNER);
            var book = inventory.join("book", JoinType.INNER);

            return cb.or(
                    cb.like(cb.lower(book.get("title")), value),
                    cb.like(cb.lower(book.get("isbn13")), value),
                    cb.like(cb.lower(book.get("isbn10")), value)
            );
        };
    }

    private long value(Long value) {
        return value != null ? value : 0L;
    }
}
