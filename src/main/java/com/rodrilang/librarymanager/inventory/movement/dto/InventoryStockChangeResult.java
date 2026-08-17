package com.rodrilang.librarymanager.inventory.movement.dto;

import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.model.InventoryMovement;

public record InventoryStockChangeResult(
        Inventory inventory,
        InventoryMovement movement
) {
}