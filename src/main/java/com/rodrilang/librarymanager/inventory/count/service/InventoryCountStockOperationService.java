package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.inventory.cost.service.InventoryCostMovementService;
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
    private final InventoryCostMovementService inventoryCostMovementService;

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
                ? addStock(session, inventory, result.getCountedQuantity(), item)
                : setStock(session, inventory, result.getCountedQuantity(), item);

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
            inventoryCostMovementService.applyInventoryCount(session, stockResult.movement());

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

    private InventoryStockChangeResult addStock(InventoryCountSession session, Inventory inventory, int quantity, InventoryCountItem item) {
        InventoryStockChangeResult result = stockService.changeStock(
                inventory.getId(),
                new InventoryStockChangeCommand(
                        quantity,
                        InventoryMovementType.ENTRY,
                        InventoryMovementSource.MANUAL,
                        InventoryMovementReferenceType.INVENTORY_COUNT,
                        session.getId().toString(),
                        "Entrada aplicada desde conteo de inventario",
                        consignmentQuantity(session, item, quantity),
                        consignmentProviderId(session, item)
                )
        );
        inventoryCostMovementService.applyInventoryCount(session, result.movement());
        return result;
    }

    private InventoryStockChangeResult setStock(InventoryCountSession session, Inventory inventory, int targetStock, InventoryCountItem item) {
        InventoryStockChangeResult result = stockService.adjustStockTo(
                inventory.getId(),
                new InventoryStockAdjustmentCommand(
                        targetStock,
                        InventoryMovementSource.MANUAL,
                        InventoryMovementReferenceType.INVENTORY_COUNT,
                        session.getId().toString(),
                        "Ajuste aplicado desde conteo de inventario"
                )
        );
        inventoryCostMovementService.applyInventoryCount(session, result.movement());
        int targetConsignment = consignmentQuantity(session, item, targetStock);
        if (item != null && (item.getConsignmentQuantityOverride() != null || Boolean.TRUE.equals(session.getDefaultConsignment()))) {
            stockService.adjustConsignment(
                    inventory.getId(),
                    targetConsignment,
                    targetConsignment > 0 ? consignmentProviderId(session, item) : null,
                    "Consignación aplicada desde conteo de inventario"
            );
        }
        return result;
    }

    private int consignmentQuantity(InventoryCountSession session, InventoryCountItem item, int quantity) {
        if (item != null && item.getConsignmentQuantityOverride() != null) {
            return Math.min(item.getConsignmentQuantityOverride(), quantity);
        }
        return Boolean.TRUE.equals(session.getDefaultConsignment()) ? quantity : 0;
    }

    private Long consignmentProviderId(InventoryCountSession session, InventoryCountItem item) {
        if (item != null && item.getConsignmentProvider() != null) return item.getConsignmentProvider().getId();
        return session.getDefaultConsignmentProvider() != null ? session.getDefaultConsignmentProvider().getId() : null;
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
