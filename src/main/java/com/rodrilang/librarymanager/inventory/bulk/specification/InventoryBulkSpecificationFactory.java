package com.rodrilang.librarymanager.inventory.bulk.specification;

import com.rodrilang.librarymanager.enums.InventoryStockFilter;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkFilterRequest;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.model.Publisher;
import com.rodrilang.librarymanager.util.TextNormalizer;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class InventoryBulkSpecificationFactory {

    public Specification<Inventory> build(
            Long bookstoreId,
            InventoryBulkFilterRequest filter
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            Join<Inventory, Book> book = root.join("book", JoinType.INNER);

            predicates.add(
                    criteriaBuilder.equal(
                            root.get("bookstore").get("id"),
                            bookstoreId
                    )
            );

            if (filter != null) {
                if (filter.active() != null) {
                    predicates.add(
                            criteriaBuilder.equal(
                                    root.get("active"),
                                    filter.active()
                            )
                    );
                }

                if (filter.condition() != null) {
                    predicates.add(
                            criteriaBuilder.equal(
                                    root.get("condition"),
                                    filter.condition()
                            )
                    );
                }

                if (filter.publisherId() != null) {
                    predicates.add(
                            criteriaBuilder.equal(
                                    book.get("publisher").get("id"),
                                    filter.publisherId()
                            )
                    );
                }

                appendStockFilter(
                        predicates,
                        root,
                        criteriaBuilder,
                        filter.stock()
                );

                appendSearchFilter(
                        predicates,
                        book,
                        criteriaBuilder,
                        filter.q()
                );
            }

            return criteriaBuilder.and(
                    predicates.toArray(Predicate[]::new)
            );
        };
    }

    private void appendStockFilter(
            List<Predicate> predicates,
            jakarta.persistence.criteria.Root<Inventory> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            InventoryStockFilter stockFilter
    ) {
        InventoryStockFilter filter =
                stockFilter != null
                        ? stockFilter
                        : InventoryStockFilter.ALL;

        Expression<Integer> stock = root.get("stock");
        Expression<Integer> minimumStock = root.get("minimumStock");

        switch (filter) {
            case AVAILABLE -> predicates.add(
                    criteriaBuilder.greaterThan(stock, minimumStock)
            );

            case LOW -> predicates.add(
                    criteriaBuilder.and(
                            criteriaBuilder.greaterThan(stock, 0),
                            criteriaBuilder.lessThanOrEqualTo(stock, minimumStock)
                    )
            );

            case OUT -> predicates.add(
                    criteriaBuilder.equal(stock, 0)
            );

            case ALL -> {
            }
        }
    }

    private void appendSearchFilter(
            List<Predicate> predicates,
            Join<Inventory, Book> book,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String value
    ) {
        if (value == null || value.isBlank()) {
            return;
        }

        String query = value.trim();

        if (query.matches("[0-9Xx\\-\\s]+")) {
            String identifier = normalizeIdentifier(query);

            if (identifier != null) {
                predicates.add(
                        criteriaBuilder.or(
                                criteriaBuilder.like(
                                        book.get("isbn13"),
                                        identifier + "%"
                                ),
                                criteriaBuilder.like(
                                        book.get("isbn10"),
                                        identifier + "%"
                                )
                        )
                );
            }

            return;
        }

        String normalizedTitle =
                TextNormalizer.normalizeForSearch(query);

        String textLike =
                "%" + query.toLowerCase(Locale.ROOT) + "%";

        Join<Book, Publisher> publisher =
                book.join("publisher", JoinType.LEFT);

        predicates.add(
                criteriaBuilder.or(
                        criteriaBuilder.like(
                                book.get("titleSearch"),
                                "%" + normalizedTitle + "%"
                        ),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(publisher.get("name")),
                                textLike
                        )
                )
        );
    }

    private String normalizeIdentifier(String value) {
        String normalized = value
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^0-9X]", "");

        return normalized.isBlank() ? null : normalized;
    }
}