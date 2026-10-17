package com.rodrilang.librarymanager.inventory.pricing.repository;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImport;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportStatus;
import com.rodrilang.librarymanager.inventory.pricing.job.model.InventoryPriceImportJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryPriceImportRepository extends JpaRepository<InventoryPriceImport, Long> {
    @EntityGraph(attributePaths = {"format", "provider"})
    Page<InventoryPriceImport> findAllByBookstoreIdOrderByCreatedAtDesc(Long bookstoreId, Pageable pageable);

    @EntityGraph(attributePaths = {"format", "provider"})
    Page<InventoryPriceImport> findAllByBookstoreIdAndProviderIdOrderByCreatedAtDesc(
            Long bookstoreId,
            Long providerId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"format", "provider"})
    Optional<InventoryPriceImport> findByIdAndBookstoreId(Long id, Long bookstoreId);

    @EntityGraph(attributePaths = {"format", "provider"})
    @Query("""
            select priceImport
            from InventoryPriceImport priceImport
            where priceImport.bookstore.id = :bookstoreId
              and (
                    priceImport.status in :importStatuses
                    or exists (
                        select job.id
                        from InventoryPriceImportJob job
                        where job.priceImport.id = priceImport.id
                          and job.status in :jobStatuses
                    )
              )
            order by priceImport.createdAt desc
            """)
    List<InventoryPriceImport> findActiveByBookstoreId(
            @Param("bookstoreId") Long bookstoreId,
            @Param("importStatuses") Collection<InventoryPriceImportStatus> importStatuses,
            @Param("jobStatuses") Collection<InventoryPriceImportJobStatus> jobStatuses
    );

    @EntityGraph(attributePaths = {"bookstore", "format", "provider"})
    @Query("select priceImport from InventoryPriceImport priceImport where priceImport.id = :id")
    Optional<InventoryPriceImport> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select priceImport
            from InventoryPriceImport priceImport
            left join fetch priceImport.format
            left join fetch priceImport.provider
            where priceImport.id = :id
              and priceImport.bookstore.id = :bookstoreId
            """)
    Optional<InventoryPriceImport> findByIdAndBookstoreIdForUpdate(
            @Param("id") Long id,
            @Param("bookstoreId") Long bookstoreId
    );

    @Modifying
    @Query(value = """
            UPDATE inventory_price_imports
               SET status = 'FAILED',
                   processing_finished_at = NOW(),
                   processing_error = 'El proceso de preview se interrumpió y puede volver a iniciarse.',
                   updated_at = NOW()
             WHERE status = 'PREVIEW_PROCESSING'
               AND processing_started_at < :threshold
            """, nativeQuery = true)
    int failStalePreviewProcessing(@Param("threshold") Instant threshold);
}
