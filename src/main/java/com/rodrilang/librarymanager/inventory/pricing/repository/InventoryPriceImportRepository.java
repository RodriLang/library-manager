package com.rodrilang.librarymanager.inventory.pricing.repository;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryPriceImportRepository extends JpaRepository<InventoryPriceImport, Long> {
    @EntityGraph(attributePaths = {"format", "provider"})
    Page<InventoryPriceImport> findAllByBookstoreIdOrderByCreatedAtDesc(Long bookstoreId, Pageable pageable);

    @EntityGraph(attributePaths = {"format", "provider"})
    Optional<InventoryPriceImport> findByIdAndBookstoreId(Long id, Long bookstoreId);
}
