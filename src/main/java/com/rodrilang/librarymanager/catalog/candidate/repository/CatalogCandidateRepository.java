package com.rodrilang.librarymanager.catalog.candidate.repository;

import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidate;
import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CatalogCandidateRepository extends JpaRepository<CatalogCandidate, Long> {

    Optional<CatalogCandidate> findByIsbn13(String isbn13);

    @EntityGraph(attributePaths = {"resolvedBook", "firstSeenByBookstore", "resolvedByUser"})
    @Query("SELECT candidate FROM CatalogCandidate candidate WHERE (:status IS NULL OR candidate.status = :status)")
    Page<CatalogCandidate> findDetailed(@Param("status") CatalogCandidateStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"resolvedBook", "firstSeenByBookstore", "resolvedByUser"})
    Optional<CatalogCandidate> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"resolvedBook", "firstSeenByBookstore", "resolvedByUser"})
    @Query("""
            SELECT DISTINCT candidate
            FROM CatalogCandidate candidate
            WHERE (:status IS NULL OR candidate.status = :status)
              AND (
                    candidate.firstSeenByBookstore.id = :bookstoreId
                    OR EXISTS (
                        SELECT item.id
                        FROM InventoryCountItem item
                        WHERE item.catalogCandidate = candidate
                          AND item.session.bookstore.id = :bookstoreId
                    )
              )
            """)
    Page<CatalogCandidate> findVisibleByBookstore(
            @Param("bookstoreId") Long bookstoreId,
            @Param("status") CatalogCandidateStatus status,
            Pageable pageable
    );
}
