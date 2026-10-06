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
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
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
    private final ProviderRepository providerRepository;

    @Override
    @Transactional
    public InventoryStockChangeResult changeStock(Long inventoryId, InventoryStockChangeCommand command) {
        validateMovement(command);

        Inventory inventory = inventoryRepository.findByIdForUpdate(inventoryId)
                .orElseThrow(() -> new BusinessException("Inventario no encontrado"));

        int stockBefore = value(inventory.getStock());
        int consignmentBefore = value(inventory.getConsignmentStock());
        int stockAfter = stockBefore + command.quantity();

        if (stockAfter < 0) {
            throw new BusinessException("No hay stock suficiente para realizar la operación.");
        }
        if (command.type() == InventoryMovementType.INITIAL_STOCK && stockBefore != 0) {
            throw new BusinessException("El stock inicial solo puede registrarse sobre un inventario con stock cero");
        }

        int consignmentDelta = resolveConsignmentDelta(inventory, command);
        int consignmentAfter = consignmentBefore + consignmentDelta;

        if (consignmentAfter < 0) {
            throw new BusinessException("No hay suficientes unidades consignadas para realizar la operación.");
        }
        if (consignmentAfter > stockAfter) {
            throw new BusinessException("El stock consignado no puede superar el stock total.");
        }

        Provider movementProvider = resolveProvider(inventory, command, consignmentDelta, consignmentAfter);

        inventory.setStock(stockAfter);
        inventory.setConsignmentStock(consignmentAfter);
        if (consignmentAfter > 0) {
            inventory.setConsignmentProvider(movementProvider != null ? movementProvider : inventory.getConsignmentProvider());
        } else if (command.consignmentProviderId() != null || consignmentDelta != 0) {
            inventory.setConsignmentProvider(null);
        }

        InventoryMovement movement = movementRepository.save(InventoryMovement.builder()
                .inventory(inventory)
                .type(command.type())
                .source(command.source())
                .quantity(command.quantity())
                .stockBefore(stockBefore)
                .stockAfter(stockAfter)
                .consignmentDelta(consignmentDelta)
                .consignmentBefore(consignmentBefore)
                .consignmentAfter(consignmentAfter)
                .consignmentProvider(movementProvider)
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

        int consignmentBefore = value(inventory.getConsignmentStock());
        if (consignmentBefore > command.targetStock()) {
            throw new BusinessException("El stock objetivo no puede quedar por debajo del stock consignado. Ajustá primero la consignación.");
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
                .consignmentDelta(0)
                .consignmentBefore(consignmentBefore)
                .consignmentAfter(consignmentBefore)
                .consignmentProvider(inventory.getConsignmentProvider())
                .referenceType(command.referenceType())
                .referenceId(command.referenceId())
                .note(command.note())
                .build());

        inventoryCostMovementService.apply(movement);
        return new InventoryStockChangeResult(inventory, movement);
    }

    @Transactional
    public InventoryStockChangeResult adjustConsignment(Long inventoryId, int targetConsignmentStock, Long providerId, String note) {
        Inventory inventory = inventoryRepository.findByIdForUpdate(inventoryId)
                .orElseThrow(() -> new BusinessException("Inventario no encontrado"));
        if (targetConsignmentStock < 0 || targetConsignmentStock > inventory.getStock()) {
            throw new BusinessException("La cantidad consignada debe estar entre 0 y el stock total.");
        }

        int before = value(inventory.getConsignmentStock());
        if (before == targetConsignmentStock && (targetConsignmentStock == 0 || sameProvider(inventory, providerId))) {
            return new InventoryStockChangeResult(inventory, null);
        }

        Provider provider = null;
        if (targetConsignmentStock > 0) {
            if (providerId == null) throw new BusinessException("Debe indicar el proveedor de la consignación.");
            provider = requireProvider(providerId);
            if (before > 0 && inventory.getConsignmentProvider() != null
                    && !inventory.getConsignmentProvider().getId().equals(providerId)) {
                throw new BusinessException("Este inventario ya tiene stock consignado asociado a otro proveedor.");
            }
        }

        int delta = targetConsignmentStock - before;
        Provider previousProvider = inventory.getConsignmentProvider();
        inventory.setConsignmentStock(targetConsignmentStock);
        inventory.setConsignmentProvider(targetConsignmentStock > 0 ? provider : null);

        InventoryMovement movement = movementRepository.save(InventoryMovement.builder()
                .inventory(inventory)
                .type(InventoryMovementType.OWNERSHIP_ADJUSTMENT)
                .source(com.rodrilang.librarymanager.enums.InventoryMovementSource.MANUAL)
                .quantity(0)
                .stockBefore(inventory.getStock())
                .stockAfter(inventory.getStock())
                .consignmentDelta(delta)
                .consignmentBefore(before)
                .consignmentAfter(targetConsignmentStock)
                .consignmentProvider(provider != null ? provider : previousProvider)
                .note(note == null || note.isBlank() ? "Ajuste de propiedad del stock" : note.trim())
                .build());
        return new InventoryStockChangeResult(inventory, movement);
    }

    private int resolveConsignmentDelta(Inventory inventory, InventoryStockChangeCommand command) {
        if (command.consignmentDelta() != null) return command.consignmentDelta();
        if (command.type() == InventoryMovementType.SALE && command.quantity() < 0) {
            return -Math.min(value(inventory.getConsignmentStock()), Math.abs(command.quantity()));
        }
        return 0;
    }

    private Provider resolveProvider(Inventory inventory, InventoryStockChangeCommand command, int delta, int after) {
        Long providerId = command.consignmentProviderId();
        if (delta > 0) {
            if (providerId == null) throw new BusinessException("Debe indicar el proveedor de las unidades consignadas.");
            Provider provider = requireProvider(providerId);
            if (value(inventory.getConsignmentStock()) > 0 && inventory.getConsignmentProvider() != null
                    && !inventory.getConsignmentProvider().getId().equals(providerId)) {
                throw new BusinessException("Este inventario ya tiene unidades consignadas asociadas a otro proveedor.");
            }
            return provider;
        }
        if (delta < 0) {
            if (inventory.getConsignmentProvider() == null) {
                throw new BusinessException("El inventario no tiene proveedor de consignación asociado.");
            }
            if (providerId != null && !inventory.getConsignmentProvider().getId().equals(providerId)) {
                throw new BusinessException("El proveedor no coincide con la consignación del inventario.");
            }
            return inventory.getConsignmentProvider();
        }
        return after > 0 ? inventory.getConsignmentProvider() : null;
    }

    private Provider requireProvider(Long id) {
        return providerRepository.findById(id)
                .filter(Provider::isPurchasable)
                .orElseThrow(() -> new BusinessException("El proveedor de consignación no existe o no está activo."));
    }

    private boolean sameProvider(Inventory inventory, Long providerId) {
        return inventory.getConsignmentProvider() != null && inventory.getConsignmentProvider().getId().equals(providerId);
    }

    private int value(Integer value) { return value == null ? 0 : value; }

    private void validateMovement(InventoryStockChangeCommand command) {
        if (command == null) throw new BusinessException("Debe especificarse el movimiento de stock");
        if (command.type() == null) throw new BusinessException("Debe especificarse el tipo de movimiento");
        if (command.source() == null) throw new BusinessException("Debe especificarse el origen del movimiento");
        if (command.quantity() == 0 && command.type() != InventoryMovementType.OWNERSHIP_ADJUSTMENT) {
            throw new BusinessException("El movimiento de stock no puede tener cantidad cero");
        }
        validateQuantitySign(command);
        validateReference(command.referenceType(), command.referenceId());
    }

    private void validateAdjustment(InventoryStockAdjustmentCommand command) {
        if (command == null) throw new BusinessException("Debe especificarse el ajuste de stock");
        if (command.targetStock() < 0) throw new BusinessException("El stock objetivo no puede ser negativo");
        if (command.source() == null) throw new BusinessException("Debe especificarse el origen del ajuste");
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
            case ADJUSTMENT, OWNERSHIP_ADJUSTMENT -> { }
        }
    }

    private void requireNegativeQuantity(InventoryStockChangeCommand command) {
        if (command.quantity() >= 0) throw new BusinessException(command.type() + " requiere una cantidad negativa");
    }

    private void requirePositiveQuantity(InventoryStockChangeCommand command) {
        if (command.quantity() <= 0) throw new BusinessException(command.type() + " requiere una cantidad positiva");
    }
}
