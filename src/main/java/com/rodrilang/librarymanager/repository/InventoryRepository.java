package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.model.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    boolean existsByBookIdAndBookstoreIdAndCondition(Long bookId, Long bookStoreId, BookCondition condition);

    @EntityGraph(attributePaths = {
            "book",
            "book.publisher",
            "book.authors"

    })

    @Query("SELECT i FROM Inventory i")
    Page<Inventory> findAllWithBookDetails(Pageable pageable);


    @EntityGraph(attributePaths = {
            "book",
            "book.publisher",
            "book.authors",
            "bookstore"
    })
    Optional<Inventory> findWithBookDetailsByBookIdAndBookstoreIdAndCondition(
            Long bookId,
            Long bookstoreId,
            BookCondition condition
    );

    @Query(
            value = """
                    SELECT i.*
                    FROM inventory i
                    JOIN books b ON b.id = i.book_id
                    LEFT JOIN publishers p ON p.id = b.publisher_id
                    WHERE
                        lower(b.isbn) LIKE lower(concat('%', :query, '%'))
                        OR unaccent(lower(b.title)) LIKE unaccent(lower(concat('%', :query, '%')))
                        OR unaccent(lower(coalesce(b.subtitle, ''))) LIKE unaccent(lower(concat('%', :query, '%')))
                        OR unaccent(lower(p.name)) LIKE unaccent(lower(concat('%', :query, '%')))
                        OR EXISTS (
                            SELECT 1
                            FROM book_authors ba
                            JOIN authors a ON a.id = ba.author_id
                            WHERE ba.book_id = b.id
                              AND unaccent(lower(a.name)) LIKE unaccent(lower(concat('%', :query, '%')))
                        )
                        OR similarity(unaccent(lower(b.title)), unaccent(lower(:query))) > 0.25
                        OR similarity(unaccent(lower(coalesce(b.subtitle, ''))), unaccent(lower(:query))) > 0.25
                        OR similarity(unaccent(lower(coalesce(p.name, ''))), unaccent(lower(:query))) > 0.25
                        OR EXISTS (
                            SELECT 1
                            FROM book_authors ba
                            JOIN authors a ON a.id = ba.author_id
                            WHERE ba.book_id = b.id
                              AND similarity(unaccent(lower(a.name)), unaccent(lower(:query))) > 0.25
                        )
                    ORDER BY
                        CASE
                            WHEN lower(b.isbn) = lower(:query) THEN 1
                            WHEN lower(b.isbn) LIKE lower(concat(:query, '%')) THEN 2
                            WHEN unaccent(lower(b.title)) LIKE unaccent(lower(concat(:query, '%'))) THEN 3
                            WHEN EXISTS (
                                SELECT 1
                                FROM book_authors ba
                                JOIN authors a ON a.id = ba.author_id
                                WHERE ba.book_id = b.id
                                  AND unaccent(lower(a.name)) LIKE unaccent(lower(concat(:query, '%')))
                            ) THEN 4
                            WHEN unaccent(lower(p.name)) LIKE unaccent(lower(concat(:query, '%'))) THEN 5
                            ELSE 6
                        END,
                        GREATEST(
                            similarity(unaccent(lower(b.title)), unaccent(lower(:query))),
                            similarity(unaccent(lower(coalesce(b.subtitle, ''))), unaccent(lower(:query))),
                            similarity(unaccent(lower(coalesce(p.name, ''))), unaccent(lower(:query)))
                        ) DESC,
                        b.title
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM inventory i
                    JOIN books b ON b.id = i.book_id
                    LEFT JOIN publishers p ON p.id = b.publisher_id
                    WHERE
                        lower(b.isbn) LIKE lower(concat('%', :query, '%'))
                        OR unaccent(lower(b.title)) LIKE unaccent(lower(concat('%', :query, '%')))
                        OR unaccent(lower(coalesce(b.subtitle, ''))) LIKE unaccent(lower(concat('%', :query, '%')))
                        OR unaccent(lower(p.name)) LIKE unaccent(lower(concat('%', :query, '%')))
                        OR EXISTS (
                            SELECT 1
                            FROM book_authors ba
                            JOIN authors a ON a.id = ba.author_id
                            WHERE ba.book_id = b.id
                              AND unaccent(lower(a.name)) LIKE unaccent(lower(concat('%', :query, '%')))
                        )
                        OR similarity(unaccent(lower(b.title)), unaccent(lower(:query))) > 0.25
                        OR similarity(unaccent(lower(coalesce(b.subtitle, ''))), unaccent(lower(:query))) > 0.25
                        OR similarity(unaccent(lower(coalesce(p.name, ''))), unaccent(lower(:query))) > 0.25
                        OR EXISTS (
                            SELECT 1
                            FROM book_authors ba
                            JOIN authors a ON a.id = ba.author_id
                            WHERE ba.book_id = b.id
                              AND similarity(unaccent(lower(a.name)), unaccent(lower(:query))) > 0.25
                        )
                    """,
            nativeQuery = true
    )
    Page<Inventory> search(@Param("query") String query, Pageable pageable);
}
