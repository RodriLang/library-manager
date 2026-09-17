package com.rodrilang.librarymanager.inventory.cost.repository;

import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostAllocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryCostAllocationRepository extends JpaRepository<InventoryCostAllocation, Long> {

    boolean existsByInventoryMovementId(Long inventoryMovementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"costLayer", "inventoryMovement"})
    @Query("""
            SELECT allocation
            FROM InventoryCostAllocation allocation
            WHERE allocation.inventoryMovement.inventory.id = :inventoryId
              AND allocation.inventoryMovement.referenceType = :referenceType
              AND allocation.inventoryMovement.referenceId = :referenceId
              AND allocation.reversedAt IS NULL
            ORDER BY allocation.id ASC
            """)
    List<InventoryCostAllocation> findActiveByMovementReferenceForUpdate(
            @Param("inventoryId") Long inventoryId,
            @Param("referenceType") InventoryMovementReferenceType referenceType,
            @Param("referenceId") String referenceId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"costLayer", "inventoryMovement"})
    @Query("""
            SELECT allocation
            FROM InventoryCostAllocation allocation
            WHERE allocation.inventoryMovement.inventory.id = :inventoryId
              AND allocation.inventoryMovement.referenceType = :referenceType
              AND allocation.inventoryMovement.referenceId = :referenceId
              AND allocation.reversedAt IS NULL
            ORDER BY allocation.id DESC
            """)
    List<InventoryCostAllocation> findActiveCountAllocationsForUpdate(
            @Param("inventoryId") Long inventoryId,
            @Param("referenceType") InventoryMovementReferenceType referenceType,
            @Param("referenceId") String referenceId
    );

    @Query("""
            SELECT COUNT(allocation)
            FROM InventoryCostAllocation allocation
            JOIN allocation.costLayer layer
            JOIN layer.sourceMovement sourceMovement
            WHERE sourceMovement.referenceType = :referenceType
              AND sourceMovement.referenceId = :referenceId
              AND allocation.reversedAt IS NULL
              AND (
                    allocation.inventoryMovement.referenceType IS NULL
                    OR allocation.inventoryMovement.referenceId IS NULL
                    OR allocation.inventoryMovement.referenceType <> :referenceType
                    OR allocation.inventoryMovement.referenceId <> :referenceId
              )
            """)
    long countExternalActiveAllocationsFromSourceReference(
            @Param("referenceType") InventoryMovementReferenceType referenceType,
            @Param("referenceId") String referenceId
    );
}
