package com.rodrilang.librarymanager.inventory.bulk.service.action;

import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkActionRequest;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkAction;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkMutationResult;
import com.rodrilang.librarymanager.model.Inventory;

public interface InventoryBulkActionHandler {

    boolean supports(InventoryBulkAction action);

    InventoryBulkMutationResult apply(
            Inventory inventory,
            InventoryBulkActionRequest request
    );
}