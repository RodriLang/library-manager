package com.rodrilang.librarymanager.inventory.pricing.repository;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

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
}
