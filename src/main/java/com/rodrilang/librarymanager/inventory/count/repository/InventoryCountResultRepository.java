package com.rodrilang.librarymanager.inventory.count.repository;

import com.rodrilang.librarymanager.inventory.count.model.InventoryCountDifferenceType;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryCountResultRepository extends JpaRepository<InventoryCountResult, Long> {

    @EntityGraph(attributePaths = {"book", "inventory"})
    List<InventoryCountResult> findAllBySessionIdOrderById(Long sessionId);

    @EntityGraph(attributePaths = {"book", "inventory"})
    List<InventoryCountResult> findAllBySessionIdAndCountedQuantityIsNotNullOrderById(Long sessionId);

    @EntityGraph(attributePaths = {"book", "inventory"})
    Page<InventoryCountResult> findAllBySessionIdAndCountedQuantityIsNotNull(Long sessionId, Pageable pageable);

    @EntityGraph(attributePaths = {"book", "inventory"})
    Page<InventoryCountResult> findAllBySessionIdAndCountedQuantityIsNotNullAndDifferenceType(
            Long sessionId,
            InventoryCountDifferenceType differenceType,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"book", "inventory"})
    @Query("""
            SELECT result
            FROM InventoryCountResult result
            JOIN result.book book
            WHERE result.session.id = :sessionId
              AND result.countedQuantity IS NOT NULL
              AND (:difference IS NULL OR result.differenceType = :difference)
              AND (
                    LOWER(book.title) LIKE CONCAT('%', :query, '%')
                    OR (
                        :searchIdentifier = true
                        AND (
                            book.isbn13 LIKE CONCAT('%', :identifier, '%')
                            OR book.isbn10 LIKE CONCAT('%', :identifier, '%')
                        )
                    )
              )
            """)
    Page<InventoryCountResult> search(
            @Param("sessionId") Long sessionId,
            @Param("difference") InventoryCountDifferenceType difference,
            @Param("query") String query,
            @Param("searchIdentifier") boolean searchIdentifier,
            @Param("identifier") String identifier,
            Pageable pageable
    );

    Optional<InventoryCountResult> findBySessionIdAndBookId(Long sessionId, Long bookId);

    void deleteBySessionIdAndBookIdAndBaselineFalseAndAppliedAtIsNull(Long sessionId, Long bookId);

    long countBySessionIdAndCountedQuantityIsNotNull(Long sessionId);

    long countBySessionIdAndDifferenceType(Long sessionId, InventoryCountDifferenceType differenceType);

    @Query("""
            SELECT COALESCE(SUM(result.previousQuantity), 0)
            FROM InventoryCountResult result
            WHERE result.session.id = :sessionId
              AND result.countedQuantity IS NOT NULL
            """)
    long sumPreviousQuantity(@Param("sessionId") Long sessionId);

    @Query("""
            SELECT COALESCE(SUM(result.countedQuantity), 0)
            FROM InventoryCountResult result
            WHERE result.session.id = :sessionId
              AND result.countedQuantity IS NOT NULL
            """)
    long sumCountedQuantity(@Param("sessionId") Long sessionId);

    @Modifying
    @Query("""
            DELETE FROM InventoryCountResult result
            WHERE result.session.id = :sessionId
              AND result.baseline = false
              AND result.appliedAt IS NULL
            """)
    void deleteUnappliedNonBaselineBySessionId(@Param("sessionId") Long sessionId);
}
