package com.rodrilang.librarymanager.inventory.movement.dto;

import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;

import java.time.Instant;

public record InventoryMovementResponse(

        Long id,

        Long inventoryId,

        Long bookId,
        String isbn,
        String title,
        String coverUrl,

        InventoryMovementType type,
        InventoryMovementSource source,

        Integer quantity,
        Integer stockBefore,
        Integer stockAfter,

        InventoryMovementReferenceType referenceType,
        String referenceId,

        String note,

        Instant createdAt

) {
}