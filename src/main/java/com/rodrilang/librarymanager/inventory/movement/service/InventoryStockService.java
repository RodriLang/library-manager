package com.rodrilang.librarymanager.inventory.movement.service;

import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeCommand;
import com.rodrilang.librarymanager.model.Inventory;

public interface InventoryStockService {

    Inventory changeStock(
            Long inventoryId,
            InventoryStockChangeCommand command
    );

    Inventory adjustStockTo(
            Long inventoryId,
            int targetStock,
            InventoryMovementSource source,
            String note
    );
}