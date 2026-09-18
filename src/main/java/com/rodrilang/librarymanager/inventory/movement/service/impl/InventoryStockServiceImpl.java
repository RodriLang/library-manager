package com.rodrilang.librarymanager.inventory.movement.service.impl;

import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.cost.service.InventoryCostMovementService;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockAdjustmentCommand;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeCommand;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeResult;
import com.rodrilang.librarymanager.inventory.movement.repository.InventoryMovementRepository;
import com.rodrilang.librarymanager.inventory.movement.service.InventoryStockService;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.model.InventoryMovement;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryStockServiceImpl implements InventoryStockService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryCostMovementService inventoryCostMovementService;

    @Override
    @Transactional
    public InventoryStockChangeResult changeStock(Long inventoryId, InventoryStockChangeCommand command) {
        validateMovement(command);

        Inventory inventory = inventoryRepository.findByIdForUpdate(inventoryId)
                .orElseThrow(() -> new BusinessException("Inventario no encontrado"));

        int stockBefore = inventory.getStock();
        int stockAfter = stockBefore + command.quantity();

        if (stockAfter < 0) {
            throw new BusinessException("No hay stock suficiente para realizar la operación.");
        }

        if (command.type() == InventoryMovementType.INITIAL_STOCK && stockBefore != 0) {
            throw new BusinessException("El stock inicial solo puede registrarse sobre un inventario con stock cero");
        }

        inventory.setStock(stockAfter);

        InventoryMovement movement = movementRepository.save(InventoryMovement.builder()
                .inventory(inventory)
                .type(command.type())
                .source(command.source())
                .quantity(command.quantity())
                .stockBefore(stockBefore)
                .stockAfter(stockAfter)
                .referenceType(command.referenceType())
                .referenceId(command.referenceId())
                .note(command.note())
                .build());

        inventoryCostMovementService.apply(movement);
        return new InventoryStockChangeResult(inventory, movement);
    }

    @Override
    @Transactional
    public InventoryStockChangeResult adjustStockTo(Long inventoryId, InventoryStockAdjustmentCommand command) {
        validateAdjustment(command);

        Inventory inventory = inventoryRepository.findByIdForUpdate(inventoryId)
                .orElseThrow(() -> new BusinessException("Inventario no encontrado"));

        int stockBefore = inventory.getStock();
        if (stockBefore == command.targetStock()) {
            return new InventoryStockChangeResult(inventory, null);
        }

        int quantity = command.targetStock() - stockBefore;
        inventory.setStock(command.targetStock());

        InventoryMovement movement = movementRepository.save(InventoryMovement.builder()
                .inventory(inventory)
                .type(InventoryMovementType.ADJUSTMENT)
                .source(command.source())
                .quantity(quantity)
                .stockBefore(stockBefore)
                .stockAfter(command.targetStock())
                .referenceType(command.referenceType())
                .referenceId(command.referenceId())
                .note(command.note())
                .build());

        inventoryCostMovementService.apply(movement);
        return new InventoryStockChangeResult(inventory, movement);
    }

    private void validateMovement(InventoryStockChangeCommand command) {
        if (command == null) {
            throw new BusinessException("Debe especificarse el movimiento de stock");
        }
        if (command.type() == null) {
            throw new BusinessException("Debe especificarse el tipo de movimiento");
        }
        if (command.source() == null) {
            throw new BusinessException("Debe especificarse el origen del movimiento");
        }
        if (command.quantity() == 0) {
            throw new BusinessException("El movimiento de stock no puede tener cantidad cero");
        }

        validateQuantitySign(command);
        validateReference(command.referenceType(), command.referenceId());
    }

    private void validateAdjustment(InventoryStockAdjustmentCommand command) {
        if (command == null) {
            throw new BusinessException("Debe especificarse el ajuste de stock");
        }
        if (command.targetStock() < 0) {
            throw new BusinessException("El stock objetivo no puede ser negativo");
        }
        if (command.source() == null) {
            throw new BusinessException("Debe especificarse el origen del ajuste");
        }

        validateReference(command.referenceType(), command.referenceId());
    }

    private void validateReference(InventoryMovementReferenceType referenceType, String referenceId) {
        boolean hasReferenceType = referenceType != null;
        boolean hasReferenceId = referenceId != null && !referenceId.isBlank();

        if (hasReferenceType != hasReferenceId) {
            throw new BusinessException("El tipo y el identificador de referencia deben informarse juntos");
        }
    }

    private void validateQuantitySign(InventoryStockChangeCommand command) {
        switch (command.type()) {
            case SALE, DAMAGE, LOSS -> requireNegativeQuantity(command);
            case INITIAL_STOCK, ENTRY, PURCHASE, RETURN -> requirePositiveQuantity(command);
            case ADJUSTMENT -> {
                // Puede ser positivo o negativo.
            }
        }
    }

    private void requireNegativeQuantity(InventoryStockChangeCommand command) {
        if (command.quantity() >= 0) {
            throw new BusinessException(command.type() + " requiere una cantidad negativa");
        }
    }

    private void requirePositiveQuantity(InventoryStockChangeCommand command) {
        if (command.quantity() <= 0) {
            throw new BusinessException(command.type() + " requiere una cantidad positiva");
        }
    }
}
