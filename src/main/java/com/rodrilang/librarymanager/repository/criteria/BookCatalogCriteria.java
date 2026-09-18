package com.rodrilang.librarymanager.repository.criteria;

import com.rodrilang.librarymanager.enums.EditorialPricePresence;

import java.math.BigDecimal;

public record BookCatalogCriteria(
        String query,
        boolean force,
        Long publisherId,
        Long authorId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        EditorialPricePresence priceStatus
) {

    public BookCatalogCriteria {
        query = normalizeQuery(query);
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
                null,
                null,
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
                null,
                null,
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
}