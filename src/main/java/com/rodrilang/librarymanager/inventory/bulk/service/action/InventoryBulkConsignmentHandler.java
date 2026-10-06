package com.rodrilang.librarymanager.inventory.bulk.service.action;

import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkActionRequest;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkAction;
import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkMutationResult;
import com.rodrilang.librarymanager.inventory.movement.repository.InventoryMovementRepository;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.model.InventoryMovement;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.service.ProviderAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryBulkConsignmentHandler implements InventoryBulkActionHandler {
    private final ProviderAccessService providerAccessService;
    private final InventoryMovementRepository movementRepository;

    @Override
    public boolean supports(InventoryBulkAction action) {
        return action == InventoryBulkAction.MARK_AS_CONSIGNMENT || action == InventoryBulkAction.CLEAR_CONSIGNMENT;
    }

    @Override
    public InventoryBulkMutationResult apply(Inventory inventory, InventoryBulkActionRequest request) {
        int before = inventory.getConsignmentStock() == null ? 0 : inventory.getConsignmentStock();
        if (request.action() == InventoryBulkAction.CLEAR_CONSIGNMENT) {
            if (before == 0) return InventoryBulkMutationResult.skipped();
            Provider previousProvider = inventory.getConsignmentProvider();
            inventory.setConsignmentStock(0);
            inventory.setConsignmentProvider(null);
            saveMovement(inventory, before, 0, previousProvider, "Consignación removida por edición masiva");
            return InventoryBulkMutationResult.modified();
        }

        Provider provider = providerAccessService.requireUsableByCurrentBookstore(request.consignmentProviderId());
        if (before > 0 && inventory.getConsignmentProvider() != null
                && !inventory.getConsignmentProvider().getId().equals(provider.getId())) {
            throw new BusinessException("Un inventario seleccionado ya tiene consignación de otro proveedor.");
        }
        int target = inventory.getStock();
        if (target == 0) return InventoryBulkMutationResult.skipped();
        if (before == target && inventory.getConsignmentProvider() != null
                && inventory.getConsignmentProvider().getId().equals(provider.getId())) {
            return InventoryBulkMutationResult.skipped();
        }
        inventory.setConsignmentStock(target);
        inventory.setConsignmentProvider(provider);
        saveMovement(inventory, before, target, provider, "Stock marcado como consignado por edición masiva");
        return InventoryBulkMutationResult.modified();
    }

    private void saveMovement(Inventory inventory, int before, int after, Provider provider, String note) {
        movementRepository.save(InventoryMovement.builder()
                .inventory(inventory)
                .type(InventoryMovementType.OWNERSHIP_ADJUSTMENT)
                .source(InventoryMovementSource.MANUAL)
                .quantity(0)
                .stockBefore(inventory.getStock())
                .stockAfter(inventory.getStock())
                .consignmentDelta(after - before)
                .consignmentBefore(before)
                .consignmentAfter(after)
                .consignmentProvider(provider)
                .note(note)
                .build());
    }
}
