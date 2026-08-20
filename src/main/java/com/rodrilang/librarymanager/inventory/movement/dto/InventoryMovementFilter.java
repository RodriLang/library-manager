package com.rodrilang.librarymanager.inventory.movement.dto;

import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;

import java.time.Instant;

public record InventoryMovementFilter(

        String query,
        Long inventoryId,
        InventoryMovementType type,
        InventoryMovementSource source,
        Instant from,
        Instant to

) {
}