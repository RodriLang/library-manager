package com.rodrilang.librarymanager.dto.request;

import com.rodrilang.librarymanager.enums.EditorialPricePresence;
import com.rodrilang.librarymanager.repository.criteria.BookCatalogCriteria;

import java.math.BigDecimal;

public record BookCatalogFilterRequest(
        String q,
        Long publisherId,
        Long authorId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        EditorialPricePresence priceStatus
) {

    public BookCatalogCriteria toCriteria(boolean force) {
        return new BookCatalogCriteria(
                q,
                force,
                publisherId,
                authorId,
                minPrice,
                maxPrice,
                priceStatus
        );
    }
}