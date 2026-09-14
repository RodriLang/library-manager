package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountMode;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountResult;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockAdjustmentCommand;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeCommand;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeResult;
import com.rodrilang.librarymanager.inventory.movement.service.InventoryStockService;
import com.rodrilang.librarymanager.model.Inventory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InventoryCountStockOperationService {

    private final InventoryCountProvisioningService provisioningService;
    private final InventoryStockService stockService;

    public Optional<Long> applyInitial(InventoryCountSession session, InventoryCountResult result, InventoryCountItem item) {
        if (result.getCountedQuantity() == null) {
            return Optional.empty();
        }
        if (item != null && item.getStatus() != InventoryCountItemStatus.RESOLVED) {
            return Optional.empty();
        }

        Inventory inventory = result.getInventory();
        if (inventory == null && result.getCountedQuantity() == 0) {
            markAppliedWithoutInventory(result, item);
            return Optional.empty();
        }
        if (inventory == null) {
            inventory = provisioningService.findOrCreate(item);
            result.setInventory(inventory);
        }

        boolean reactivated = !Boolean.TRUE.equals(inventory.getActive());
        if (reactivated) {
            inventory.setActive(true);
        }

        InventoryStockChangeResult stockResult = session.getMode() == InventoryCountMode.ADDITIVE
                ? addStock(session, inventory, result.getCountedQuantity())
                : setStock(session, inventory, result.getCountedQuantity());

        int delta = stockResult.movement() != null ? stockResult.movement().getQuantity() : 0;
        result.setAppliedDelta(delta);
        result.setResultingQuantity(stockResult.inventory().getStock());
        result.setResultingActive(Boolean.TRUE.equals(stockResult.inventory().getActive()));
        result.setAppliedAt(Instant.now());

        if (item != null) {
            item.setAppliedAt(Instant.now());
        }

        return delta != 0 || reactivated ? Optional.of(inventory.getId()) : Optional.empty();
    }

    public Optional<Long> applyAbsoluteCorrection(
            InventoryCountSession session,
            InventoryCountResult result,
            InventoryCountItem item,
            int previousCountedQuantity
    ) {
        Inventory inventory = result.getInventory();
        if (inventory == null) {
            inventory = provisioningService.findOrCreate(item);
            result.setInventory(inventory);
        }

        boolean reactivated = !Boolean.TRUE.equals(inventory.getActive());
        if (reactivated) {
            inventory.setActive(true);
        }

        int correction = item.getQuantity() - previousCountedQuantity;
        if (correction != 0) {
            InventoryStockChangeResult stockResult = stockService.changeStock(
                    inventory.getId(),
                    new InventoryStockChangeCommand(
                            correction,
                            InventoryMovementType.ADJUSTMENT,
                            InventoryMovementSource.MANUAL,
                            InventoryMovementReferenceType.INVENTORY_COUNT,
                            session.getId().toString(),
                            "Resolución posterior del conteo de inventario"
                    )
            );

            result.setAppliedDelta(result.getAppliedDelta() + correction);
            result.setResultingQuantity(stockResult.inventory().getStock());
        } else {
            result.setResultingQuantity(inventory.getStock());
        }

        result.setResultingActive(true);
        result.setCountedQuantity(item.getQuantity());
        item.setAppliedAt(Instant.now());

        return correction != 0 || reactivated ? Optional.of(inventory.getId()) : Optional.empty();
    }

    private InventoryStockChangeResult addStock(InventoryCountSession session, Inventory inventory, int quantity) {
        return stockService.changeStock(
                inventory.getId(),
                new InventoryStockChangeCommand(
                        quantity,
                        InventoryMovementType.ENTRY,
                        InventoryMovementSource.MANUAL,
                        InventoryMovementReferenceType.INVENTORY_COUNT,
                        session.getId().toString(),
                        "Entrada aplicada desde conteo de inventario"
                )
        );
    }

    private InventoryStockChangeResult setStock(InventoryCountSession session, Inventory inventory, int targetStock) {
        return stockService.adjustStockTo(
                inventory.getId(),
                new InventoryStockAdjustmentCommand(
                        targetStock,
                        InventoryMovementSource.MANUAL,
                        InventoryMovementReferenceType.INVENTORY_COUNT,
                        session.getId().toString(),
                        "Ajuste aplicado desde conteo de inventario"
                )
        );
    }

    private void markAppliedWithoutInventory(InventoryCountResult result, InventoryCountItem item) {
        result.setAppliedDelta(0);
        result.setResultingQuantity(0);
        result.setResultingActive(false);
        result.setAppliedAt(Instant.now());

        if (item != null) {
            item.setAppliedAt(Instant.now());
        }
    }
}
