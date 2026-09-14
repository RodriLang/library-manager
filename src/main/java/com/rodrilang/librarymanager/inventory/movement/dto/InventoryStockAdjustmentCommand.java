package com.rodrilang.librarymanager.inventory.movement.dto;

import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.enums.InventoryMovementSource;

public record InventoryStockAdjustmentCommand(

        int targetStock,

        InventoryMovementSource source,

        InventoryMovementReferenceType referenceType,

        String referenceId,

        String note

) {
}
