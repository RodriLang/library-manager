package com.rodrilang.librarymanager.repository.criteria;

import com.rodrilang.librarymanager.enums.EditorialPricePresence;

import java.math.BigDecimal;
import java.util.List;

public record BookCatalogCriteria(
        String query,
        boolean force,
        List<Long> publisherIds,
        List<Long> authorIds,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        EditorialPricePresence priceStatus
) {

    public BookCatalogCriteria {
        query = normalizeQuery(query);
        publisherIds = normalizeIds(publisherIds);
        authorIds = normalizeIds(authorIds);
        priceStatus = priceStatus == null
                ? EditorialPricePresence.ALL
                : priceStatus;
    }

    public boolean hasQuery() {
        return query != null;
    }

    public boolean hasPriceFilter() {
        return minPrice != null
                || maxPrice != null
                || priceStatus != EditorialPricePresence.ALL;
    }

    public static BookCatalogCriteria empty() {
        return new BookCatalogCriteria(
                null,
                false,
                List.of(),
                List.of(),
                null,
                null,
                EditorialPricePresence.ALL
        );
    }

    public static BookCatalogCriteria search(
            String query,
            boolean force
    ) {
        return new BookCatalogCriteria(
                query,
                force,
                List.of(),
                List.of(),
                null,
                null,
                EditorialPricePresence.ALL
        );
    }

    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }

        return query.trim();
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }
}
