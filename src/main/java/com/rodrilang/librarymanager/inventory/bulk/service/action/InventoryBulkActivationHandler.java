package com.rodrilang.librarymanager.inventory.bulk.service.action;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkActionRequest;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkAction;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkMutationResult;
import com.rodrilang.librarymanager.model.Inventory;
import org.springframework.stereotype.Component;

@Component
public class InventoryBulkActivationHandler
        implements InventoryBulkActionHandler {

    @Override
    public boolean supports(InventoryBulkAction action) {
        return action == InventoryBulkAction.ACTIVATE
                || action == InventoryBulkAction.DEACTIVATE;
    }

    @Override
    public InventoryBulkMutationResult apply(
            Inventory inventory,
            InventoryBulkActionRequest request
    ) {
        boolean targetActive =
                request.action() == InventoryBulkAction.ACTIVATE;

        if (Boolean.TRUE.equals(inventory.getActive())
                == targetActive) {
            return InventoryBulkMutationResult.skipped();
        }

        inventory.setActive(targetActive);

        boolean linked =
                inventory.getTiendanubeStatus()
                        == TiendanubeInventoryStatus.LINKED;

        if (!linked) {
            return InventoryBulkMutationResult.modified();
        }

        boolean syncPrice =
                targetActive
                        && Boolean.TRUE.equals(
                        inventory.getTiendanubePriceSyncEnabled()
                );

        return InventoryBulkMutationResult.modified(
                true,
                syncPrice
        );
    }
}