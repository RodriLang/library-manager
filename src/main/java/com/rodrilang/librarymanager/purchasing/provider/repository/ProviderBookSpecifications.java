package com.rodrilang.librarymanager.purchasing.provider.repository;

import com.rodrilang.librarymanager.importer.price.configuration.model.ProviderBook;
import org.springframework.data.jpa.domain.Specification;

public final class ProviderBookSpecifications {

    private ProviderBookSpecifications() {
    }

    public static Specification<ProviderBook> providerId(Long providerId) {

        return (root, query, cb) ->
                cb.equal(root.get("provider").get("id"), providerId);
    }

    public static Specification<ProviderBook> active() {

        return (root, query, cb) ->
                cb.isTrue(root.get("active"));
    }

    public static Specification<ProviderBook> activeBook() {

        return (root, query, cb) ->
                cb.isTrue(root.get("book").get("active"));
    }

    public static Specification<ProviderBook> search(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = "%" + value.trim().toLowerCase() + "%";

        return (root, query, cb) -> {

            var book = root.join("book");

            return cb.or(
                    cb.like(cb.lower(book.get("title")), normalized),
                    cb.like(cb.lower(cb.coalesce(book.get("isbn13"), "")), normalized),
                    cb.like(cb.lower(cb.coalesce(book.get("isbn10"), "")), normalized),
                    cb.like(cb.lower(cb.coalesce(root.get("externalCode"), "")), normalized)
            );
        };
    }
}