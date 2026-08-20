package com.rodrilang.librarymanager.inventory.movement.service;

import com.rodrilang.librarymanager.inventory.movement.dto.InventoryMovementFilter;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryMovementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InventoryMovementQueryService {

    Page<InventoryMovementResponse> findAll(
            InventoryMovementFilter filter,
            Pageable pageable
    );
}