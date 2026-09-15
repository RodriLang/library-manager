package com.rodrilang.librarymanager.inventory.bulk.service.action;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkActionRequest;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkAction;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkMutationResult;
import com.rodrilang.librarymanager.model.Inventory;
import org.springframework.stereotype.Component;

@Component
public class InventoryBulkEditorialPriceSyncHandler
        implements InventoryBulkActionHandler {

    @Override
    public boolean supports(InventoryBulkAction action) {
        return action == InventoryBulkAction.ENABLE_EDITORIAL_PRICE_SYNC
                || action == InventoryBulkAction.DISABLE_EDITORIAL_PRICE_SYNC;
    }

    @Override
    public InventoryBulkMutationResult apply(
            Inventory inventory,
            InventoryBulkActionRequest request
    ) {
        boolean enable =
                request.action()
                        == InventoryBulkAction.ENABLE_EDITORIAL_PRICE_SYNC;

        if (enable
                && inventory.getCondition() != BookCondition.NEW) {
            return InventoryBulkMutationResult.skipped();
        }

        boolean current =
                Boolean.TRUE.equals(
                        inventory.getEditorialPriceSyncEnabled()
                );

        if (current == enable) {
            return InventoryBulkMutationResult.skipped();
        }

        inventory.setEditorialPriceSyncEnabled(enable);

        return InventoryBulkMutationResult.modified();
    }
}