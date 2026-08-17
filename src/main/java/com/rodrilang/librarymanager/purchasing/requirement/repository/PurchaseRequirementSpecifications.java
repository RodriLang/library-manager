package com.rodrilang.librarymanager.purchasing.requirement.repository;

import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirement;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementStatus;
import org.springframework.data.jpa.domain.Specification;

public final class PurchaseRequirementSpecifications {

    private PurchaseRequirementSpecifications() {
    }

    public static Specification<PurchaseRequirement> bookstoreId(Long bookstoreId) {

        if (bookstoreId == null) {
            return null;
        }

        return (root, query, cb) ->
                cb.equal(root.get("bookstore").get("id"), bookstoreId);
    }

    public static Specification<PurchaseRequirement> status(PurchaseRequirementStatus status) {

        if (status == null) {
            return null;
        }

        return (root, query, cb) ->
                cb.equal(root.get("status"), status);
    }

    public static Specification<PurchaseRequirement> providerId(Long providerId) {

        if (providerId == null) {
            return null;
        }

        return (root, query, cb) ->
                cb.equal(root.get("preferredProvider").get("id"), providerId);
    }

    public static Specification<PurchaseRequirement> search(String query) {

        if (query == null || query.isBlank()) {
            return null;
        }

        String normalized = "%" + query.trim().toLowerCase() + "%";

        return (root, criteriaQuery, cb) -> {

            var book = root.join("book");

            return cb.or(
                    cb.like(cb.lower(book.get("title")), normalized),
                    cb.like(cb.lower(cb.coalesce(book.get("isbn13"), "")), normalized),
                    cb.like(cb.lower(cb.coalesce(book.get("isbn10"), "")), normalized)
            );
        };
    }
}