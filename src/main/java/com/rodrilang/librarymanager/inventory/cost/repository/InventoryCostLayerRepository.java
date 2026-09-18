package com.rodrilang.librarymanager.inventory.cost.repository;

import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostLayer;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Optional;

public interface InventoryCostLayerRepository
        extends JpaRepository<InventoryCostLayer, Long>, JpaSpecificationExecutor<InventoryCostLayer> {

    @Override
    @EntityGraph(attributePaths = {"inventory", "inventory.book"})
    Page<InventoryCostLayer> findAll(@Nullable Specification<InventoryCostLayer> spec, Pageable pageable);

    Optional<InventoryCostLayer> findBySourceMovementId(Long sourceMovementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"inventory", "inventory.book"})
    @Query("""
            SELECT layer
            FROM InventoryCostLayer layer
            WHERE layer.id = :layerId
              AND layer.inventory.bookstore.id = :bookstoreId
              AND layer.reversedAt IS NULL
            """)
    Optional<InventoryCostLayer> findByIdAndBookstoreIdForUpdate(
            @Param("layerId") Long layerId,
            @Param("bookstoreId") Long bookstoreId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT layer
            FROM InventoryCostLayer layer
            WHERE layer.inventory.id = :inventoryId
              AND layer.reversedAt IS NULL
              AND layer.quantityRemaining > 0
            ORDER BY layer.enteredAt ASC, layer.id ASC
            """)
    List<InventoryCostLayer> findAvailableByInventoryIdForUpdate(@Param("inventoryId") Long inventoryId);

    @Query("""
            SELECT COALESCE(SUM(layer.quantityRemaining), 0)
            FROM InventoryCostLayer layer
            WHERE layer.inventory.id = :inventoryId
              AND layer.reversedAt IS NULL
            """)
    long sumRemainingByInventoryId(@Param("inventoryId") Long inventoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT layer
            FROM InventoryCostLayer layer
            JOIN layer.sourceMovement movement
            WHERE layer.inventory.id = :inventoryId
              AND layer.reversedAt IS NULL
              AND movement.referenceType = :referenceType
              AND movement.referenceId = :referenceId
            ORDER BY layer.id ASC
            """)
    List<InventoryCostLayer> findActiveBySourceMovementReferenceForUpdate(
            @Param("inventoryId") Long inventoryId,
            @Param("referenceType") InventoryMovementReferenceType referenceType,
            @Param("referenceId") String referenceId
    );
}
