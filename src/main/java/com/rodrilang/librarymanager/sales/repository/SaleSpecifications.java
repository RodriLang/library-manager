package com.rodrilang.librarymanager.sales.repository;

import com.rodrilang.librarymanager.sales.model.Sale;
import com.rodrilang.librarymanager.sales.model.SaleOrigin;
import com.rodrilang.librarymanager.sales.model.SaleStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class SaleSpecifications {

    private SaleSpecifications() {
    }

    public static Specification<Sale> bookstoreId(Long bookstoreId) {
        return (root, query, cb) ->
                cb.equal(root.get("bookstore").get("id"), bookstoreId);
    }

    public static Specification<Sale> status(SaleStatus status) {
        if (status == null) {
            return null;
        }

        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Sale> origin(SaleOrigin origin) {
        if (origin == null) {
            return null;
        }

        return (root, query, cb) -> cb.equal(root.get("origin"), origin);
    }

    public static Specification<Sale> soldAtFrom(Instant from) {
        if (from == null) {
            return null;
        }

        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("soldAt"), from);
    }

    public static Specification<Sale> soldAtTo(Instant to) {
        if (to == null) {
            return null;
        }

        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("soldAt"), to);
    }
}
