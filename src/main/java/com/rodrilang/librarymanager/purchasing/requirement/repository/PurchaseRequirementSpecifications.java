package com.rodrilang.librarymanager.purchasing.requirement.repository;

import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderItem;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderStatus;
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

    public static Specification<PurchaseRequirement> hasRemainingQuantity() {

        return (root, query, cb) -> {
            var orderedQuantity = query.subquery(Long.class);
            var item = orderedQuantity.from(PurchaseOrderItem.class);

            orderedQuantity.select(
                    cb.coalesce(
                            cb.sumAsLong(item.<Integer>get("requirementQuantity")),
                            0L
                    )
            );

            orderedQuantity.where(
                    cb.equal(item.get("requirement").get("id"), root.get("id")),
                    cb.notEqual(item.get("purchaseOrder").get("status"), PurchaseOrderStatus.CANCELLED)
            );

            return cb.gt(root.<Integer>get("quantity"), orderedQuantity);
        };
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