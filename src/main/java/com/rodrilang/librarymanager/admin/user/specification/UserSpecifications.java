package com.rodrilang.librarymanager.admin.user.specification;

import com.rodrilang.librarymanager.auth.models.User;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> matchesSearch(String search) {
        if (search == null || search.isBlank()) {
            return Specification.unrestricted();
        }

        String normalizedSearch =
                search.trim().toLowerCase(Locale.ROOT);

        String pattern = "%" + normalizedSearch + "%";

        return (root, query, cb) ->
                cb.or(
                        cb.like(cb.lower(root.get("username")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("firstName")), pattern),
                        cb.like(cb.lower(root.get("lastName")), pattern),
                        cb.like(cb.lower(
                                cb.concat(
                                        cb.concat(root.get("firstName"), " "),
                                        root.get("lastName"))), pattern)
                );
    }

    public static Specification<User> hasEnabled(Boolean enabled) {
        if (enabled == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.equal(root.get("enabled"), enabled);
    }

    public static Specification<User> hasLocked(Boolean locked) {
        if (locked == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.equal(root.get("accountLocked"), locked);
    }

    public static Specification<User> belongsToBookstore(
            Long bookstoreId
    ) {
        if (bookstoreId == null) {
            return Specification.unrestricted();
        }

        return (root, query, cb) ->
                cb.equal(root.get("bookstore").get("id"), bookstoreId);
    }
}