package com.rodrilang.librarymanager.inventory.movement.repository;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.model.InventoryMovement;
import io.micrometer.common.lang.NonNullApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.lang.Nullable;

import java.time.Instant;

@NonNullApi
public interface InventoryMovementRepository
        extends JpaRepository<InventoryMovement, Long>,
        JpaSpecificationExecutor<InventoryMovement> {

    @Override
    @EntityGraph(attributePaths = {
            "inventory",
            "inventory.book"
    })
    Page<InventoryMovement> findAll(
            @Nullable Specification<InventoryMovement> spec,
            Pageable pageable
    );

    boolean existsByInventoryIdAndTypeAndReferenceTypeAndReferenceId(
            Long inventoryId,
            InventoryMovementType type,
            InventoryMovementReferenceType referenceType,
            String referenceId
    );

    @Query("""
            SELECT COUNT(movement)
            FROM InventoryMovement movement
            WHERE movement.inventory.bookstore.id = :bookstoreId
              AND movement.inventory.condition = :condition
              AND movement.createdAt > :after
              AND (
                    movement.referenceType IS NULL
                    OR movement.referenceId IS NULL
                    OR movement.referenceType <> :countReferenceType
                    OR movement.referenceId <> :sessionReferenceId
              )
            """)
    long countConcurrentAfter(
            @Param("bookstoreId") Long bookstoreId,
            @Param("condition") BookCondition condition,
            @Param("after") Instant after,
            @Param("countReferenceType") InventoryMovementReferenceType countReferenceType,
            @Param("sessionReferenceId") String sessionReferenceId
    );
}