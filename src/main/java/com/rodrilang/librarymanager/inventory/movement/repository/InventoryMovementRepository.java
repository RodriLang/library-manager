package com.rodrilang.librarymanager.inventory.movement.repository;

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
import org.springframework.lang.Nullable;

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
}