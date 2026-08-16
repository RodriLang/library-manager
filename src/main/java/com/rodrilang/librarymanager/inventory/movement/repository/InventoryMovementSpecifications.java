package com.rodrilang.librarymanager.inventory.movement.repository;

import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.model.InventoryMovement;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class InventoryMovementSpecifications {

    private InventoryMovementSpecifications() {
    }

    public static Specification<InventoryMovement> inventoryId(Long inventoryId) {
        return (root, query, cb) -> {
            if (inventoryId == null) {
                return null;
            }

            return cb.equal(root.get("inventory").get("id"), inventoryId);
        };
    }

    public static Specification<InventoryMovement> bookstoreId(Long bookstoreId) {
        return (root, query, cb) ->
                cb.equal(
                        root.get("inventory").get("bookstore").get("id"),
                        bookstoreId
                );
    }

    public static Specification<InventoryMovement> type(
            InventoryMovementType type
    ) {
        return (root, query, cb) -> {
            if (type == null) {
                return null;
            }

            return cb.equal(root.get("type"), type);
        };
    }

    public static Specification<InventoryMovement> source(
            InventoryMovementSource source
    ) {
        return (root, query, cb) -> {
            if (source == null) {
                return null;
            }

            return cb.equal(root.get("source"), source);
        };
    }

    public static Specification<InventoryMovement> from(Instant from) {
        return (root, query, cb) -> {
            if (from == null) {
                return null;
            }

            return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
        };
    }

    public static Specification<InventoryMovement> to(Instant to) {
        return (root, query, cb) -> {
            if (to == null) {
                return null;
            }

            return cb.lessThanOrEqualTo(root.get("createdAt"), to);
        };
    }

    public static Specification<InventoryMovement> search(String value) {
        return (root, query, cb) -> {

            if (value == null || value.isBlank()) {
                return null;
            }

            String trimmed = value.trim();
            String textPattern = "%" + trimmed.toLowerCase() + "%";

            String normalizedIsbn = trimmed
                    .toUpperCase()
                    .replaceAll("[^0-9X]", "");

            var inventory = root.join("inventory");
            var book = inventory.join("book");

            if (!normalizedIsbn.isBlank()) {
                String isbnPattern = "%" + normalizedIsbn + "%";

                return cb.or(
                        cb.like(cb.lower(book.get("title")), textPattern),
                        cb.like(book.get("isbn13"), isbnPattern),
                        cb.like(book.get("isbn10"), isbnPattern)
                );
            }

            return cb.like(
                    cb.lower(book.get("title")),
                    textPattern
            );
        };
    }
}