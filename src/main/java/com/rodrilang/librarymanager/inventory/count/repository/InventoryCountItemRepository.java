package com.rodrilang.librarymanager.inventory.count.repository;

import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryCountItemRepository extends JpaRepository<InventoryCountItem, Long> {

    @EntityGraph(attributePaths = {"book", "catalogCandidate"})
    Optional<InventoryCountItem> findByIdAndSessionId(Long id, Long sessionId);

    @EntityGraph(attributePaths = {"book", "catalogCandidate"})
    Page<InventoryCountItem> findAllBySessionId(Long sessionId, Pageable pageable);

    @EntityGraph(attributePaths = {"book", "catalogCandidate"})
    Page<InventoryCountItem> findAllBySessionIdAndStatus(
            Long sessionId,
            InventoryCountItemStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"book", "catalogCandidate"})
    @Query("""
            SELECT item
            FROM InventoryCountItem item
            LEFT JOIN item.book book
            WHERE item.session.id = :sessionId
              AND (:status IS NULL OR item.status = :status)
              AND (
                    LOWER(book.title) LIKE CONCAT('%', :query, '%')
                    OR (
                        :identifier IS NOT NULL
                        AND (
                            item.isbn13 LIKE CONCAT('%', :identifier, '%')
                            OR item.isbn10 LIKE CONCAT('%', :identifier, '%')
                            OR item.normalizedIdentifier LIKE CONCAT('%', :identifier, '%')
                        )
                    )
              )
            """)
    Page<InventoryCountItem> search(
            @Param("sessionId") Long sessionId,
            @Param("status") InventoryCountItemStatus status,
            @Param("query") String query,
            @Param("identifier") String identifier,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"book", "catalogCandidate", "session", "session.bookstore"})
    List<InventoryCountItem> findAllBySessionIdOrderById(Long sessionId);

    @EntityGraph(attributePaths = {"book", "catalogCandidate", "session", "session.bookstore"})
    List<InventoryCountItem> findAllByCatalogCandidateId(Long catalogCandidateId);

    @EntityGraph(attributePaths = {"book", "catalogCandidate", "session", "session.bookstore"})
    List<InventoryCountItem> findAllByCatalogCandidateIdIn(Collection<Long> catalogCandidateIds);

    Optional<InventoryCountItem> findBySessionIdAndBookId(Long sessionId, Long bookId);

    boolean existsBySessionIdAndAppliedAtIsNullAndStatusNot(Long sessionId, InventoryCountItemStatus status);

    boolean existsByCatalogCandidateIdAndSessionBookstoreId(Long catalogCandidateId, Long bookstoreId);

    @Query("""
            SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END
            FROM InventoryCountItem item
            WHERE item.catalogCandidate.id = :catalogCandidateId
              AND item.session.bookstore.id = :bookstoreId
              AND item.appliedAt IS NULL
              AND item.status = com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus.PENDING_CATALOG
              AND item.session.status IN :sessionStatuses
            """)
    boolean existsOperationalPendingCatalogCandidate(
            @Param("catalogCandidateId") Long catalogCandidateId,
            @Param("bookstoreId") Long bookstoreId,
            @Param("sessionStatuses") Collection<InventoryCountStatus> sessionStatuses
    );

    @Query("""
            SELECT CASE WHEN COUNT(item) > 0 THEN true ELSE false END
            FROM InventoryCountItem item
            WHERE item.session.bookstore.id = :bookstoreId
              AND item.session.condition = :condition
              AND item.session.status IN :sessionStatuses
              AND item.appliedAt IS NULL
              AND item.status <> com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus.SUPERSEDED
              AND (
                    item.book.id = :bookId
                    OR (:isbn13 IS NOT NULL AND item.isbn13 = :isbn13)
                    OR (:isbn10 IS NOT NULL AND item.isbn10 = :isbn10)
              )
            """)
    boolean existsActiveInventoryCountIntentForBook(
            @Param("bookId") Long bookId,
            @Param("isbn13") String isbn13,
            @Param("isbn10") String isbn10,
            @Param("bookstoreId") Long bookstoreId,
            @Param("condition") com.rodrilang.librarymanager.enums.BookCondition condition,
            @Param("sessionStatuses") Collection<InventoryCountStatus> sessionStatuses
    );

    @EntityGraph(attributePaths = {"book", "session", "session.bookstore"})
    @Query("""
            SELECT item
            FROM InventoryCountItem item
            WHERE item.book.id IN :bookIds
              AND item.appliedAt IS NULL
              AND item.status <> com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus.SUPERSEDED
              AND item.session.status IN :sessionStatuses
            """)
    List<InventoryCountItem> findUnappliedByBookIdsAndSessionStatuses(
            @Param("bookIds") Collection<Long> bookIds,
            @Param("sessionStatuses") Collection<InventoryCountStatus> sessionStatuses
    );

    long countBySessionIdAndStatus(Long sessionId, InventoryCountItemStatus status);

    @EntityGraph(attributePaths = {"book", "session", "session.bookstore"})
    @Query("""
            SELECT item
            FROM InventoryCountItem item
            WHERE item.session.id = :sessionId
              AND item.status IN :statuses
            """)
    List<InventoryCountItem> findAllBySessionIdAndStatusIn(
            @Param("sessionId") Long sessionId,
            @Param("statuses") Collection<InventoryCountItemStatus> statuses
    );

    @Query(value = """
            SELECT
                COUNT(*) AS "totalItems",
                COALESCE(SUM(quantity), 0) AS "totalUnits",
                COUNT(*) FILTER (WHERE status = 'RESOLVED') AS "resolvedItems",
                COUNT(*) FILTER (WHERE status = 'PENDING_CATALOG') AS "pendingCatalogItems",
                COUNT(*) FILTER (WHERE status = 'PENDING_PRICE') AS "pendingPriceItems",
                COUNT(*) FILTER (WHERE status = 'INVALID_IDENTIFIER') AS "invalidItems",
                COUNT(*) FILTER (WHERE status = 'SUPERSEDED') AS "supersededItems",
                COUNT(*) FILTER (WHERE applied_at IS NOT NULL) AS "appliedItems"
            FROM inventory_count_items
            WHERE session_id = :sessionId
            """, nativeQuery = true)
    InventoryCountItemSummaryProjection summarize(@Param("sessionId") Long sessionId);
}
