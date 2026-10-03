package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.inventory.pricing.dto.InventoryPriceDashboardSummaryResponse;
import com.rodrilang.librarymanager.inventory.pricing.dto.InventoryPriceOverviewResponse;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPrice;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceRepository;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryPriceOverviewService {

    private static final int DEFAULT_STALE_DAYS = 90;
    private static final BigDecimal REVIEW_PERCENT = BigDecimal.valueOf(50);

    private final InventoryRepository inventoryRepository;
    private final InventoryPriceRepository priceRepository;
    private final BookstoreContext bookstoreContext;
    private final InventoryPriceService inventoryPriceService;

    @Transactional(readOnly = true)
    public Page<InventoryPriceOverviewResponse> find(String q, String status, Integer staleDays, Pageable pageable) {
        List<InventoryPriceOverviewResponse> rows = buildRows(staleDays);
        String normalizedQuery = normalize(q);
        Predicate<InventoryPriceOverviewResponse> queryPredicate = row -> normalizedQuery.isBlank()
                || normalize(row.title()).contains(normalizedQuery)
                || normalize(row.isbn()).contains(normalizedQuery)
                || row.authors().stream().anyMatch(author -> normalize(author).contains(normalizedQuery));
        Predicate<InventoryPriceOverviewResponse> statusPredicate = statusPredicate(status);

        List<InventoryPriceOverviewResponse> filtered = rows.stream()
                .filter(queryPredicate)
                .filter(statusPredicate)
                .toList();

        int from = Math.min((int) pageable.getOffset(), filtered.size());
        int to = Math.min(from + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(from, to), pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public InventoryPriceDashboardSummaryResponse summary(Integer staleDays) {
        List<InventoryPriceOverviewResponse> rows = buildRows(staleDays);
        return new InventoryPriceDashboardSummaryResponse(
                rows.size(),
                rows.stream().filter(row -> !row.missingPrice()).count(),
                rows.stream().filter(InventoryPriceOverviewResponse::missingPrice).count(),
                rows.stream().filter(InventoryPriceOverviewResponse::stale).count(),
                rows.stream().filter(InventoryPriceOverviewResponse::scheduled).count(),
                rows.stream().filter(InventoryPriceOverviewResponse::increased).count(),
                rows.stream().filter(InventoryPriceOverviewResponse::decreased).count(),
                rows.stream().filter(InventoryPriceOverviewResponse::reviewRequired).count()
        );
    }

    private List<InventoryPriceOverviewResponse> buildRows(Integer staleDays) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        LocalDate today = inventoryPriceService.today();
        LocalDate staleBefore = today.minusDays(staleDays != null && staleDays > 0 ? staleDays : DEFAULT_STALE_DAYS);

        List<Inventory> inventories = inventoryRepository.findAllByBookstoreIdAndActiveTrue(bookstoreId);
        Map<Long, List<InventoryPrice>> pricesByInventory = priceRepository.findAllByBookstoreId(bookstoreId)
                .stream()
                .collect(Collectors.groupingBy(price -> price.getInventory().getId(), LinkedHashMap::new, Collectors.toList()));

        return inventories.stream()
                .map(inventory -> toRow(inventory, pricesByInventory.getOrDefault(inventory.getId(), List.of()), today, staleBefore))
                .sorted(Comparator.comparing(InventoryPriceOverviewResponse::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private InventoryPriceOverviewResponse toRow(
            Inventory inventory,
            List<InventoryPrice> timeline,
            LocalDate today,
            LocalDate staleBefore
    ) {
        List<InventoryPrice> currentAndPast = timeline.stream()
                .filter(price -> !price.getEffectiveFrom().isAfter(today))
                .sorted(Comparator.comparing(InventoryPrice::getEffectiveFrom).reversed()
                        .thenComparing(InventoryPrice::getId, Comparator.reverseOrder()))
                .toList();
        List<InventoryPrice> future = timeline.stream()
                .filter(price -> price.getEffectiveFrom().isAfter(today))
                .sorted(Comparator.comparing(InventoryPrice::getEffectiveFrom).thenComparing(InventoryPrice::getId))
                .toList();

        InventoryPrice current = currentAndPast.isEmpty() ? null : currentAndPast.get(0);
        InventoryPrice previous = currentAndPast.size() > 1 ? currentAndPast.get(1) : null;
        InventoryPrice next = future.isEmpty() ? null : future.get(0);

        BigDecimal currentChange = percent(previous != null ? previous.getAmount() : null, current != null ? current.getAmount() : null);
        BigDecimal nextChange = percent(current != null ? current.getAmount() : null, next != null ? next.getAmount() : null);
        boolean stale = inventory.getLastPriceCheckedAt() == null || inventory.getLastPriceCheckedAt().isBefore(staleBefore);
        boolean review = isLarge(currentChange) || isLarge(nextChange);

        return new InventoryPriceOverviewResponse(
                inventory.getId(),
                inventory.getBook().getId(),
                inventory.getBook().getPreferredIsbn(),
                inventory.getBook().getTitle(),
                inventory.getBook().getAuthors().stream().map(Author::getName).sorted().toList(),
                inventory.getBook().getPublisher() != null ? inventory.getBook().getPublisher().getName() : null,
                current != null ? current.getAmount() : null,
                current != null ? current.getEffectiveFrom() : null,
                previous != null ? previous.getAmount() : null,
                currentChange,
                next != null ? next.getAmount() : null,
                next != null ? next.getEffectiveFrom() : null,
                inventory.getLastPriceCheckedAt(),
                current == null,
                stale,
                currentChange != null && currentChange.signum() > 0,
                currentChange != null && currentChange.signum() < 0,
                next != null,
                review
        );
    }

    private Predicate<InventoryPriceOverviewResponse> statusPredicate(String status) {
        if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
            return row -> true;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "MISSING" -> InventoryPriceOverviewResponse::missingPrice;
            case "STALE" -> InventoryPriceOverviewResponse::stale;
            case "SCHEDULED" -> InventoryPriceOverviewResponse::scheduled;
            case "INCREASED" -> InventoryPriceOverviewResponse::increased;
            case "DECREASED" -> InventoryPriceOverviewResponse::decreased;
            case "REVIEW" -> InventoryPriceOverviewResponse::reviewRequired;
            default -> row -> true;
        };
    }

    private BigDecimal percent(BigDecimal previous, BigDecimal next) {
        if (previous == null || next == null || previous.signum() == 0) {
            return null;
        }
        return next.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private boolean isLarge(BigDecimal percent) {
        return percent != null && percent.abs().compareTo(REVIEW_PERCENT) >= 0;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
