package com.rodrilang.librarymanager.inventory.movement.service.impl;

import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeCommand;
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

    @Override
    @Transactional
    public Inventory changeStock(
            Long inventoryId,
            InventoryStockChangeCommand command
    ) {

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

        InventoryMovement movement = InventoryMovement.builder()
                .inventory(inventory)
                .type(command.type())
                .source(command.source())
                .quantity(command.quantity())
                .stockBefore(stockBefore)
                .stockAfter(stockAfter)
                .referenceType(command.referenceType())
                .referenceId(command.referenceId())
                .note(command.note())
                .build();

        movementRepository.save(movement);

        return inventory;
    }

    @Override
    @Transactional
    public Inventory adjustStockTo(
            Long inventoryId,
            int targetStock,
            InventoryMovementSource source,
            String note
    ) {

        if (targetStock < 0) {
            throw new BusinessException("El stock objetivo no puede ser negativo");
        }

        if (source == null) {
            throw new BusinessException("Debe especificarse el origen del ajuste");
        }

        Inventory inventory = inventoryRepository.findByIdForUpdate(inventoryId)
                .orElseThrow(() -> new BusinessException("Inventario no encontrado"));

        int stockBefore = inventory.getStock();

        if (stockBefore == targetStock) {
            return inventory;
        }

        int quantity = targetStock - stockBefore;

        inventory.setStock(targetStock);

        InventoryMovement movement = InventoryMovement.builder()
                .inventory(inventory)
                .type(InventoryMovementType.ADJUSTMENT)
                .source(source)
                .quantity(quantity)
                .stockBefore(stockBefore)
                .stockAfter(targetStock)
                .note(note)
                .build();

        movementRepository.save(movement);

        return inventory;
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

        boolean hasReferenceType = command.referenceType() != null;
        boolean hasReferenceId = command.referenceId() != null && !command.referenceId().isBlank();

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