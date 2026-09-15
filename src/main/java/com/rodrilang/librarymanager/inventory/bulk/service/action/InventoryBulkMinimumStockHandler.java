package com.rodrilang.librarymanager.inventory.bulk.service.action;

import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkActionRequest;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkAction;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkMutationResult;
import com.rodrilang.librarymanager.model.Inventory;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class InventoryBulkMinimumStockHandler
        implements InventoryBulkActionHandler {

    @Override
    public boolean supports(InventoryBulkAction action) {
        return action == InventoryBulkAction.SET_MINIMUM_STOCK;
    }

    @Override
    public InventoryBulkMutationResult apply(
            Inventory inventory,
            InventoryBulkActionRequest request
    ) {
        if (Objects.equals(
                inventory.getMinimumStock(),
                request.minimumStock()
        )) {
            return InventoryBulkMutationResult.skipped();
        }

        inventory.setMinimumStock(
                request.minimumStock()
        );

        return InventoryBulkMutationResult.modified();
    }
}