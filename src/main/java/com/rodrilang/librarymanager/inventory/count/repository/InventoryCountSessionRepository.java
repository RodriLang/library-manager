package com.rodrilang.librarymanager.inventory.count.repository;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountMode;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface InventoryCountSessionRepository extends JpaRepository<InventoryCountSession, Long> {

    @EntityGraph(attributePaths = {"bookstore", "createdByUser"})
    Optional<InventoryCountSession> findByIdAndBookstoreId(Long id, Long bookstoreId);

    @EntityGraph(attributePaths = {"bookstore", "createdByUser"})
    Page<InventoryCountSession> findAllByBookstoreId(Long bookstoreId, Pageable pageable);

    boolean existsByBookstoreIdAndConditionAndModeAndStatusIn(
            Long bookstoreId,
            BookCondition condition,
            InventoryCountMode mode,
            Collection<InventoryCountStatus> statuses
    );

    boolean existsByBookstoreIdAndConditionAndModeAndStatusInAndAppliedAtAfter(
            Long bookstoreId,
            BookCondition condition,
            InventoryCountMode mode,
            Collection<InventoryCountStatus> statuses,
            Instant appliedAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT session
            FROM InventoryCountSession session
            JOIN FETCH session.bookstore
            JOIN FETCH session.createdByUser
            WHERE session.id = :sessionId
              AND session.bookstore.id = :bookstoreId
            """)
    Optional<InventoryCountSession> findByIdAndBookstoreIdForUpdate(
            @Param("sessionId") Long sessionId,
            @Param("bookstoreId") Long bookstoreId
    );
}
