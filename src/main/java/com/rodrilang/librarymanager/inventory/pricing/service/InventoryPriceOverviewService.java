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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
                .sorted(sortComparator(pageable.getSort()))
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
                rows.stream().filter(InventoryPriceOverviewResponse::confirmedThisMonth).count(),
                rows.stream().filter(row -> !row.missingPrice() && !row.confirmedThisMonth()).count(),
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

        InventoryPrice current = currentAndPast.isEmpty() ? null : currentAndPast.getFirst();
        InventoryPrice previous = currentAndPast.size() > 1 ? currentAndPast.get(1) : null;
        InventoryPrice next = future.isEmpty() ? null : future.getFirst();

        BigDecimal currentChange = percent(previous != null ? previous.getAmount() : null, current != null ? current.getAmount() : null);
        BigDecimal nextChange = percent(current != null ? current.getAmount() : null, next != null ? next.getAmount() : null);
        LocalDate lastConfirmedAt = current != null ? current.getLastConfirmedAt() : null;
        Long daysSinceConfirmation = lastConfirmedAt != null
                ? Math.max(0L, ChronoUnit.DAYS.between(lastConfirmedAt, today))
                : null;
        boolean confirmedThisMonth = current != null && isSameMonth(lastConfirmedAt, today);
        boolean stale = current != null && (lastConfirmedAt == null || lastConfirmedAt.isBefore(staleBefore));
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
                current != null ? current.getSource() : null,
                lastConfirmedAt,
                current != null ? current.getLastConfirmedSource() : null,
                daysSinceConfirmation,
                confirmedThisMonth,
                previous != null ? previous.getAmount() : null,
                previous != null ? previous.getEffectiveFrom() : null,
                currentChange,
                next != null ? next.getAmount() : null,
                next != null ? next.getEffectiveFrom() : null,
                current == null,
                stale,
                currentChange != null && currentChange.signum() > 0,
                currentChange != null && currentChange.signum() < 0,
                next != null,
                review
        );
    }

    private Comparator<InventoryPriceOverviewResponse> sortComparator(Sort sort) {
        Sort.Order order = sort.stream().findFirst().orElse(Sort.Order.asc("title"));
        String property = order.getProperty();
        boolean ascending = order.isAscending();

        Comparator<InventoryPriceOverviewResponse> comparator = switch (property) {
            case "currentPriceLastConfirmedAt" -> Comparator.comparing(
                    InventoryPriceOverviewResponse::currentPriceLastConfirmedAt,
                    ascending
                            ? Comparator.nullsFirst(Comparator.naturalOrder())
                            : Comparator.nullsLast(Comparator.reverseOrder())
            );
            case "currentPrice" -> Comparator.comparing(
                    InventoryPriceOverviewResponse::currentPrice,
                    ascending
                            ? Comparator.nullsLast(Comparator.naturalOrder())
                            : Comparator.nullsLast(Comparator.reverseOrder())
            );
            case "currentChangePercent" -> Comparator.comparing(
                    (InventoryPriceOverviewResponse row) -> row.currentChangePercent() != null ? row.currentChangePercent().abs() : null,
                    ascending
                            ? Comparator.nullsLast(Comparator.naturalOrder())
                            : Comparator.nullsLast(Comparator.reverseOrder())
            );
            case "title" -> ascending
                    ? Comparator.comparing(InventoryPriceOverviewResponse::title, String.CASE_INSENSITIVE_ORDER)
                    : Comparator.comparing(InventoryPriceOverviewResponse::title, String.CASE_INSENSITIVE_ORDER).reversed();
            default -> Comparator.comparing(InventoryPriceOverviewResponse::title, String.CASE_INSENSITIVE_ORDER);
        };

        return comparator.thenComparing(InventoryPriceOverviewResponse::title, String.CASE_INSENSITIVE_ORDER);
    }

    private Predicate<InventoryPriceOverviewResponse> statusPredicate(String status) {
        if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
            return row -> true;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "MISSING" -> InventoryPriceOverviewResponse::missingPrice;
            case "STALE" -> InventoryPriceOverviewResponse::stale;
            case "FRESH", "CURRENT" -> row -> !row.missingPrice() && !row.stale();
            case "CONFIRMED_THIS_MONTH", "UPDATED_THIS_MONTH" -> InventoryPriceOverviewResponse::confirmedThisMonth;
            case "NOT_CONFIRMED_THIS_MONTH", "NOT_UPDATED_THIS_MONTH" ->
                    row -> !row.missingPrice() && !row.confirmedThisMonth();
            case "UNCONFIRMED" -> row -> !row.missingPrice() && row.currentPriceLastConfirmedAt() == null;
            case "SCHEDULED" -> InventoryPriceOverviewResponse::scheduled;
            case "INCREASED" -> InventoryPriceOverviewResponse::increased;
            case "DECREASED" -> InventoryPriceOverviewResponse::decreased;
            case "REVIEW" -> InventoryPriceOverviewResponse::reviewRequired;
            default -> row -> true;
        };
    }

    private boolean isSameMonth(LocalDate date, LocalDate reference) {
        return date != null
                && date.getYear() == reference.getYear()
                && date.getMonth() == reference.getMonth();
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
