package com.rodrilang.librarymanager.inventory.movement.dto;

import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;

public record InventoryStockChangeCommand(

        int quantity,
        InventoryMovementType type,
        InventoryMovementSource source,
        InventoryMovementReferenceType referenceType,
        String referenceId,
        String note

) {
}