package com.rodrilang.librarymanager.repository.criteria;

import java.util.List;

public record BookCatalogCriteria(
        String query,
        boolean force,
        List<Long> publisherIds,
        List<Long> authorIds
) {

    public BookCatalogCriteria {
        query = normalizeQuery(query);
        publisherIds = normalizeIds(publisherIds);
        authorIds = normalizeIds(authorIds);
    }

    public boolean hasQuery() {
        return query != null;
    }

    public static BookCatalogCriteria empty() {
        return new BookCatalogCriteria(null, false, List.of(), List.of());
    }

    public static BookCatalogCriteria search(String query, boolean force) {
        return new BookCatalogCriteria(query, force, List.of(), List.of());
    }

    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) return null;
        return query.trim();
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream().filter(id -> id != null && id > 0).distinct().toList();
    }
}
