package com.rodrilang.librarymanager.dto.request;

import com.rodrilang.librarymanager.repository.criteria.BookCatalogCriteria;

import java.util.ArrayList;
import java.util.List;

public record BookCatalogFilterRequest(
        String q,
        List<Long> publisherIds,
        List<Long> authorIds,
        Long publisherId,
        Long authorId
) {

    public BookCatalogCriteria toCriteria(boolean force) {
        return new BookCatalogCriteria(
                q,
                force,
                mergeIds(publisherIds, publisherId),
                mergeIds(authorIds, authorId)
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
