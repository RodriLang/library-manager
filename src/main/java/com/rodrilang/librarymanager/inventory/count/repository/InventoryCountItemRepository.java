package com.rodrilang.librarymanager.inventory.count.repository;

import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
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

    @EntityGraph(attributePaths = {"book", "catalogCandidate", "session", "session.bookstore"})
    List<InventoryCountItem> findAllBySessionIdOrderById(Long sessionId);

    @EntityGraph(attributePaths = {"book", "catalogCandidate", "session", "session.bookstore"})
    List<InventoryCountItem> findAllByCatalogCandidateId(Long catalogCandidateId);

    Optional<InventoryCountItem> findBySessionIdAndBookId(Long sessionId, Long bookId);

    boolean existsBySessionIdAndAppliedAtIsNullAndStatusNot(Long sessionId, InventoryCountItemStatus status);

    boolean existsByCatalogCandidateIdAndSessionBookstoreId(Long catalogCandidateId, Long bookstoreId);

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
