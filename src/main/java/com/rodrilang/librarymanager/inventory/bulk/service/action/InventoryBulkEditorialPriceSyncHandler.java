package com.rodrilang.librarymanager.inventory.bulk.service.action;

import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkActionRequest;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkAction;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkMutationResult;
import com.rodrilang.librarymanager.model.Inventory;
import org.springframework.stereotype.Component;

/** Legacy bulk action kept for API compatibility. Editorial-price sync is no longer supported. */
@Component
public class InventoryBulkEditorialPriceSyncHandler implements InventoryBulkActionHandler {

    @Override
    public boolean supports(InventoryBulkAction action) {
        return action == InventoryBulkAction.ENABLE_EDITORIAL_PRICE_SYNC
                || action == InventoryBulkAction.DISABLE_EDITORIAL_PRICE_SYNC;
    }

    @Override
    public InventoryBulkMutationResult apply(Inventory inventory, InventoryBulkActionRequest request) {
        if (Boolean.TRUE.equals(inventory.getEditorialPriceSyncEnabled())) {
            inventory.setEditorialPriceSyncEnabled(false);
            return InventoryBulkMutationResult.modified();
        }
        return InventoryBulkMutationResult.skipped();
    }
}
