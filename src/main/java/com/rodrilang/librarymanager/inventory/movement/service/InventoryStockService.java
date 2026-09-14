package com.rodrilang.librarymanager.inventory.movement.service;

import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockAdjustmentCommand;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeCommand;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeResult;

public interface InventoryStockService {

    InventoryStockChangeResult changeStock(Long inventoryId, InventoryStockChangeCommand command);

    InventoryStockChangeResult adjustStockTo(Long inventoryId, InventoryStockAdjustmentCommand command);
}
