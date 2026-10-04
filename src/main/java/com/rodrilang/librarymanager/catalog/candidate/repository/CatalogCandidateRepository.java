package com.rodrilang.librarymanager.catalog.candidate.repository;

import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidate;
import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidateStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface CatalogCandidateRepository extends JpaRepository<CatalogCandidate, Long> {

    Optional<CatalogCandidate> findByIsbn13(String isbn13);

    @EntityGraph(attributePaths = {"resolvedBook", "firstSeenByBookstore", "resolvedByUser"})
    @Query("SELECT candidate FROM CatalogCandidate candidate WHERE (:status IS NULL OR candidate.status = :status)")
    Page<CatalogCandidate> findDetailed(@Param("status") CatalogCandidateStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"resolvedBook", "firstSeenByBookstore", "resolvedByUser"})
    Optional<CatalogCandidate> findDetailedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"resolvedBook", "firstSeenByBookstore", "resolvedByUser"})
    @Query("SELECT candidate FROM CatalogCandidate candidate WHERE candidate.id = :id")
    Optional<CatalogCandidate> findDetailedByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"resolvedBook", "firstSeenByBookstore", "resolvedByUser"})
    @Query("""
            SELECT DISTINCT candidate
            FROM CatalogCandidate candidate
            WHERE (:status IS NULL OR candidate.status = :status)
              AND EXISTS (
                    SELECT item.id
                    FROM InventoryCountItem item
                    WHERE item.catalogCandidate = candidate
                      AND item.session.bookstore.id = :bookstoreId
                      AND item.appliedAt IS NULL
                      AND item.status = :itemStatus
                      AND item.session.status IN :sessionStatuses
              )
            """)
    Page<CatalogCandidate> findOperationalPendingByBookstore(
            @Param("bookstoreId") Long bookstoreId,
            @Param("status") CatalogCandidateStatus status,
            @Param("itemStatus") InventoryCountItemStatus itemStatus,
            @Param("sessionStatuses") Collection<InventoryCountStatus> sessionStatuses,
            Pageable pageable
    );
}
