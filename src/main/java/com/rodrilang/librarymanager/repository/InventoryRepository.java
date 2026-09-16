package com.rodrilang.librarymanager.repository;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.projection.InventoryTiendanubePreviewProjection;
import io.micrometer.common.lang.NonNullApi;
import jakarta.persistence.LockModeType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@NonNullApi
public interface InventoryRepository
        extends JpaRepository<Inventory, Long>,
        JpaSpecificationExecutor<Inventory>,
        InventoryQueryRepository {

    boolean existsByBookIdAndBookstoreIdAndCondition(
            Long bookId,
            Long bookstoreId,
            BookCondition condition
    );

    boolean existsByBookId(Long bookId);

    Optional<Inventory> findByBookIdAndBookstoreIdAndCondition(
            Long bookId,
            Long bookstoreId,
            BookCondition condition
    );

    @EntityGraph(attributePaths = {
            "book",
            "bookstore"
    })
    List<Inventory> findAllByBookstoreIdAndCondition(
            Long bookstoreId,
            BookCondition condition
    );

    @EntityGraph(attributePaths = {
            "book",
            "book.authors",
            "book.publisher"
    })
    List<Inventory> findAllByBookstoreIdAndBookIdInAndActiveTrue(
            Long bookstoreId,
            Collection<Long> bookIds
    );

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
              AND b.active = true
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
    List<Inventory> findAllByBookstoreIdAndBookIdInAndCondition(
            Long bookstoreId,
            Collection<Long> bookIds,
            BookCondition condition
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
            SELECT i
            FROM Inventory i
            WHERE i.id = :id
            """)
    Optional<Inventory> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i
            FROM Inventory i
            WHERE i.bookstore.id = :bookstoreId
              AND i.id IN :inventoryIds
            ORDER BY i.id
            """)
    List<Inventory> findAllByBookstoreIdAndIdsForUpdate(
            @Param("bookstoreId") Long bookstoreId,
            @Param("inventoryIds") Collection<Long> inventoryIds
    );

    @Query("""
            SELECT i.id
            FROM Inventory i
            WHERE i.bookstore.id = :bookstoreId
              AND i.id IN :inventoryIds
            ORDER BY i.id
            """)
    List<Long> findIdsByBookstoreIdAndIdIn(
            @Param("bookstoreId") Long bookstoreId,
            @Param("inventoryIds") Collection<Long> inventoryIds
    );

    @Query("""
            SELECT i
            FROM Inventory i
            WHERE i.bookstore.id = :bookstoreId
              AND i.id IN :inventoryIds
            ORDER BY i.id
            """)
    List<Inventory> findAllByBookstoreIdAndIdIn(
            @Param("bookstoreId") Long bookstoreId,
            @Param("inventoryIds") Collection<Long> inventoryIds
    );

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

    @EntityGraph(attributePaths = {
            "book",
            "book.publisher"
    })
    @Override
    Page<Inventory> findAll(
            @Nullable Specification<Inventory> specification,
            Pageable pageable
    );
}