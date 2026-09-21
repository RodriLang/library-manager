package com.rodrilang.librarymanager.inventory.bulk.service.action;

import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkActionRequest;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkAction;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkMutationResult;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.purchasing.requirement.dto.internal.AddPurchaseRequirementCommand;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.AddPurchaseRequirementResponse;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSourceType;
import com.rodrilang.librarymanager.purchasing.requirement.service.PurchaseRequirementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryBulkReplenishmentHandler implements InventoryBulkActionHandler {

    private final PurchaseRequirementService purchaseRequirementService;

    @Override
    public boolean supports(InventoryBulkAction action) {
        return action == InventoryBulkAction.MARK_FOR_REPLENISHMENT;
    }

    @Override
    public InventoryBulkMutationResult apply(
            Inventory inventory,
            InventoryBulkActionRequest request
    ) {
        AddPurchaseRequirementResponse response = purchaseRequirementService.addManualRequirement(
                new AddPurchaseRequirementCommand(
                        inventory.getBook().getId(),
                        1,
                        PurchaseRequirementSourceType.INVENTORY,
                        null,
                        null
                )
        );

        return response.addedQuantity() > 0
                ? InventoryBulkMutationResult.modified()
                : InventoryBulkMutationResult.skipped();
    }
}
