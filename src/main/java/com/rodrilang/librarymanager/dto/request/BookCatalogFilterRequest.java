package com.rodrilang.librarymanager.dto.request;

import com.rodrilang.librarymanager.enums.EditorialPricePresence;
import com.rodrilang.librarymanager.repository.criteria.BookCatalogCriteria;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record BookCatalogFilterRequest(
        String q,
        List<Long> publisherIds,
        List<Long> authorIds,
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
                mergeIds(publisherIds, publisherId),
                mergeIds(authorIds, authorId),
                minPrice,
                maxPrice,
                priceStatus
        );
    }

    private static List<Long> mergeIds(
            List<Long> ids,
            Long legacyId
    ) {
        List<Long> result = new ArrayList<>();

        if (ids != null) {
            result.addAll(ids);
        }

        if (legacyId != null) {
            result.add(legacyId);
        }

        return result;
    }
}
