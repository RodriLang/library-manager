package com.rodrilang.librarymanager.admin.bookstore.specification;

import com.rodrilang.librarymanager.model.Bookstore;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class BookstoreSpecifications {

    private BookstoreSpecifications() {
    }

    public static Specification<Bookstore> matchesSearch(String search) {
        if (search == null || search.isBlank()) {
            return Specification.unrestricted();
        }

        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";

        return (root, query, cb) ->
                cb.like(cb.lower(root.get("name")), pattern);
    }

    public static Specification<Bookstore> hasActive(Boolean active) {
        if (active == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.equal(root.get("active"), active);
    }
}