package com.rodrilang.librarymanager.purchasing.order.repository;

import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrder;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderStatus;
import org.springframework.data.jpa.domain.Specification;

public final class PurchaseOrderSpecifications {

    private PurchaseOrderSpecifications() {
    }

    public static Specification<PurchaseOrder> bookstoreId(Long bookstoreId) {
        return (root, query, cb) ->
                cb.equal(root.get("bookstore").get("id"), bookstoreId);
    }

    public static Specification<PurchaseOrder> providerId(Long providerId) {

        if (providerId == null) {
            return null;
        }

        return (root, query, cb) ->
                cb.equal(root.get("provider").get("id"), providerId);
    }

    public static Specification<PurchaseOrder> status(PurchaseOrderStatus status) {

        if (status == null) {
            return null;
        }

        return (root, query, cb) ->
                cb.equal(root.get("status"), status);
    }

    public static Specification<PurchaseOrder> search(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String pattern = "%" + value.trim().toLowerCase() + "%";

        return (root, query, cb) ->
                cb.or(
                        cb.like(cb.lower(root.get("orderNumber")), pattern),
                        cb.like(cb.lower(root.get("provider").get("name")), pattern)
                );
    }
}
