package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.projection.InventoryTiendanubePreviewProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    boolean existsByBookIdAndBookstoreIdAndCondition(Long bookId, Long bookStoreId, BookCondition condition);

    boolean existsByBookId(Long bookId);

    @EntityGraph(attributePaths = {
            "book",
            "book.authors",
            "book.publisher"
    })
    List<Inventory> findAllByBookstoreId(Long bookstoreId);

    @EntityGraph(attributePaths = {
            "book",
            "book.publisher"
    })
    @Query("""
            SELECT DISTINCT i
            FROM Inventory i
            JOIN i.book b
            WHERE i.bookstore.id = :bookstoreId
              AND i.active = true
              AND (
                    b.isbn13 = :isbn13
                    OR (:isbn10 IS NOT NULL AND b.isbn10 = :isbn10)
              )
            """)
    List<Inventory> findAllByBookstoreAndIsbn(
            @Param("bookstoreId") Long bookstoreId,
            @Param("isbn13") String isbn13,
            @Param("isbn10") String isbn10
    );

    @EntityGraph(attributePaths = {
            "book"
    })
    List<Inventory> findAllByBookstoreIdAndBookIdInAndConditionAndActiveTrue(
            Long bookstoreId,
            Collection<Long> bookIds,
            BookCondition condition
    );

    @EntityGraph(attributePaths = {
            "book",
            "book.publisher",
            "book.authors"
    })
    Page<Inventory> findAllByBookstoreIdAndActiveTrue(
            Long bookstoreId,
            Pageable pageable
    );


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

    @EntityGraph(attributePaths = {
            "book",
            "book.publisher",
            "book.authors",
            "book.coverUrl",
            "bookstore"
    })
    @Query("""
            SELECT i
            FROM Inventory i
            WHERE i.id = :inventoryId
            """)
    Optional<Inventory> findByIdForTiendanubePublish(
            @Param("inventoryId") Long inventoryId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i
            from Inventory i
            where i.id = :id
            """)
    Optional<Inventory> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {
            "book",
            "book.authors",
            "book.publisher"
    })
    Optional<Inventory> findByIdAndBookstoreId(
            Long id,
            Long bookstoreId
    );

    @EntityGraph(attributePaths = {
            "book",
            "book.authors",
            "book.publisher"
    })
    Optional<Inventory> findByIdAndBookstoreIdAndActiveTrue(Long id, Long bookstoreId);

    @Query(
            value = """
                    SELECT i.*
                    FROM inventory i
                    JOIN books b ON b.id = i.book_id
                    WHERE i.active = true
                      AND b.active = true
                      AND i.bookstore_id = :bookstoreId
                      AND (
                            b.isbn_13 LIKE concat(:query, '%')
                            OR b.isbn_10 LIKE concat(:query, '%')
                      )
                    ORDER BY
                        CASE
                            WHEN b.isbn_13 = :query
                              OR b.isbn_10 = :query
                                THEN 1
                            ELSE 2
                        END,
                        COALESCE(b.title_sort, b.title) ASC,
                        i.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM inventory i
                    JOIN books b ON b.id = i.book_id
                    WHERE i.active = true
                      AND b.active = true
                      AND i.bookstore_id = :bookstoreId
                      AND (
                            b.isbn_13 LIKE concat(:query, '%')
                            OR b.isbn_10 LIKE concat(:query, '%')
                      )
                    """,
            nativeQuery = true
    )
    Page<Inventory> searchByIsbn(
            @Param("bookstoreId") Long bookstoreId,
            @Param("query") String query,
            Pageable pageable
    );

    @Query(
            value = """
                    WITH matches AS (
                        SELECT
                            i.id AS inventory_id,
                            CASE
                                WHEN b.title_search = :query THEN 1
                                WHEN b.title_search LIKE concat(:query, '%') THEN 2
                                ELSE 3
                            END AS priority
                        FROM inventory i
                        JOIN books b ON b.id = i.book_id
                        WHERE i.active = true
                          AND b.active = true
                          AND i.bookstore_id = :bookstoreId
                          AND to_tsvector('simple', b.title_search)
                                @@ to_tsquery('simple', :fullTextQuery)
                    
                        UNION ALL
                    
                        SELECT
                            i.id AS inventory_id,
                            4 AS priority
                        FROM inventory i
                        JOIN books b ON b.id = i.book_id
                        WHERE i.active = true
                          AND b.active = true
                          AND i.bookstore_id = :bookstoreId
                          AND immutable_unaccent(
                                  lower(coalesce(b.subtitle, ''))
                              )
                              LIKE concat(
                                  '%',
                                  immutable_unaccent(lower(:query)),
                                  '%'
                              )
                    
                        UNION ALL
                    
                        SELECT
                            i.id AS inventory_id,
                            5 AS priority
                        FROM publishers p
                        JOIN books b ON b.publisher_id = p.id
                        JOIN inventory i ON i.book_id = b.id
                        WHERE i.active = true
                          AND b.active = true
                          AND i.bookstore_id = :bookstoreId
                          AND immutable_unaccent(lower(p.name))
                              LIKE concat(
                                  '%',
                                  immutable_unaccent(lower(:query)),
                                  '%'
                              )
                    
                        UNION ALL
                    
                        SELECT
                            i.id AS inventory_id,
                            CASE
                                WHEN immutable_unaccent(lower(a.name))
                                     LIKE concat(
                                         immutable_unaccent(lower(:query)),
                                         '%'
                                     )
                                    THEN 3
                                ELSE 4
                            END AS priority
                        FROM authors a
                        JOIN book_authors ba ON ba.author_id = a.id
                        JOIN books b ON b.id = ba.book_id
                        JOIN inventory i ON i.book_id = b.id
                        WHERE i.active = true
                          AND b.active = true
                          AND i.bookstore_id = :bookstoreId
                          AND immutable_unaccent(lower(a.name))
                              LIKE concat(
                                  '%',
                                  immutable_unaccent(lower(:query)),
                                  '%'
                              )
                    ),
                    ranked_matches AS (
                        SELECT
                            inventory_id,
                            MIN(priority) AS priority
                        FROM matches
                        GROUP BY inventory_id
                    )
                    SELECT i.*
                    FROM ranked_matches rm
                    JOIN inventory i ON i.id = rm.inventory_id
                    JOIN books b ON b.id = i.book_id
                    ORDER BY
                        rm.priority ASC,
                        coalesce(b.title_sort, b.title) ASC,
                        i.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT matches.inventory_id)
                    FROM (
                        SELECT i.id AS inventory_id
                        FROM inventory i
                        JOIN books b ON b.id = i.book_id
                        WHERE i.active = true
                          AND b.active = true
                          AND i.bookstore_id = :bookstoreId
                          AND to_tsvector('simple', b.title_search)
                                @@ to_tsquery('simple', :fullTextQuery)
                    
                        UNION ALL
                    
                        SELECT i.id AS inventory_id
                        FROM inventory i
                        JOIN books b ON b.id = i.book_id
                        WHERE i.active = true
                          AND b.active = true
                          AND i.bookstore_id = :bookstoreId
                          AND immutable_unaccent(
                                  lower(coalesce(b.subtitle, ''))
                              )
                              LIKE concat(
                                  '%',
                                  immutable_unaccent(lower(:query)),
                                  '%'
                              )
                    
                        UNION ALL
                    
                        SELECT i.id AS inventory_id
                        FROM publishers p
                        JOIN books b ON b.publisher_id = p.id
                        JOIN inventory i ON i.book_id = b.id
                        WHERE i.active = true
                          AND b.active = true
                          AND i.bookstore_id = :bookstoreId
                          AND immutable_unaccent(lower(p.name))
                              LIKE concat(
                                  '%',
                                  immutable_unaccent(lower(:query)),
                                  '%'
                              )
                    
                        UNION ALL
                    
                        SELECT i.id AS inventory_id
                        FROM authors a
                        JOIN book_authors ba ON ba.author_id = a.id
                        JOIN books b ON b.id = ba.book_id
                        JOIN inventory i ON i.book_id = b.id
                        WHERE i.active = true
                          AND b.active = true
                          AND i.bookstore_id = :bookstoreId
                          AND immutable_unaccent(lower(a.name))
                              LIKE concat(
                                  '%',
                                  immutable_unaccent(lower(:query)),
                                  '%'
                              )
                    ) matches
                    """,
            nativeQuery = true
    )
    Page<Inventory> searchText(
            @Param("bookstoreId") Long bookstoreId,
            @Param("query") String query,
            @Param("fullTextQuery") String fullTextQuery,
            Pageable pageable
    );

    @Query("""
            SELECT
                i.id AS inventoryId,
                i.book.id AS bookId,
                CASE
                    WHEN COUNT(link.id) > 0 THEN true
                    ELSE false
                END AS linked
            FROM Inventory i
            LEFT JOIN TiendanubeProductLink link
                ON link.inventory.id = i.id
                AND link.active = true
            WHERE i.bookstore.id = :bookstoreId
              AND i.condition = com.rodrilang.librarymanager.enums.BookCondition.NEW
              AND i.book.id IN :bookIds
            GROUP BY
                i.id,
                i.book.id
            """)
    List<InventoryTiendanubePreviewProjection> findTiendanubePreviewByBookIds(
            @Param("bookstoreId") Long bookstoreId,
            @Param("bookIds") Collection<Long> bookIds
    );
}
