package com.rodrilang.librarymanager.inventory.cost.service;

import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostAllocation;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostLayer;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostSourceType;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostType;
import com.rodrilang.librarymanager.inventory.cost.repository.InventoryCostAllocationRepository;
import com.rodrilang.librarymanager.inventory.cost.repository.InventoryCostLayerRepository;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountPurpose;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.model.InventoryMovement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryCostMovementService {

    private final InventoryCostLayerRepository layerRepository;
    private final InventoryCostAllocationRepository allocationRepository;
    private final InventoryCostReferencePriceResolver referencePriceResolver;

    @Transactional
    public void apply(InventoryMovement movement) {
        if (movement == null || movement.getQuantity() == 0) {
            return;
        }

        if (movement.getReferenceType() == InventoryMovementReferenceType.INVENTORY_COUNT) {
            return;
        }

        if (movement.getQuantity() < 0) {
            consume(movement);
            return;
        }

        if (isSaleReturn(movement) && restoreSaleAllocations(movement)) {
            return;
        }

        registerInbound(movement, sourceType(movement));
    }

    @Transactional
    public void applyInventoryCount(InventoryCountSession session, InventoryMovement movement) {
        if (movement == null || movement.getQuantity() == 0) {
            return;
        }

        if (movement.getQuantity() < 0) {
            consume(movement);
            return;
        }

        registerInbound(movement, sourceType(session.getPurpose()));
    }

    @Transactional(readOnly = true)
    public void validateInventoryCountReversal(InventoryCountSession session) {
        String referenceId = session.getId().toString();
        long externalAllocations = allocationRepository.countExternalActiveAllocationsFromSourceReference(
                InventoryMovementReferenceType.INVENTORY_COUNT,
                referenceId
        );

        if (externalAllocations > 0) {
            throw new BusinessException(
                    "No se puede revertir el conteo porque parte del stock incorporado ya fue consumido por movimientos posteriores"
            );
        }
    }

    @Transactional
    public void reverseInventoryCountEffects(
            InventoryCountSession session,
            Inventory inventory,
            InventoryMovement reversalMovement
    ) {
        String referenceId = session.getId().toString();

        List<InventoryCostAllocation> allocations = allocationRepository.findActiveCountAllocationsForUpdate(
                inventory.getId(),
                InventoryMovementReferenceType.INVENTORY_COUNT,
                referenceId
        );
        allocations.forEach(allocation -> reverseAllocation(allocation, reversalMovement));

        List<InventoryCostLayer> layers = layerRepository.findActiveBySourceMovementReferenceForUpdate(
                inventory.getId(),
                InventoryMovementReferenceType.INVENTORY_COUNT,
                referenceId
        );

        Instant now = Instant.now();
        for (InventoryCostLayer layer : layers) {
            if (!layer.getQuantityRemaining().equals(layer.getQuantityReceived())) {
                throw new BusinessException(
                        "No se puede revertir el conteo porque una capa de costo asociada fue consumida posteriormente"
                );
            }

            layer.setQuantityRemaining(0);
            layer.setReversedAt(now);
        }
    }

    private void consume(InventoryMovement movement) {
        if (allocationRepository.existsByInventoryMovementId(movement.getId())) {
            return;
        }

        Inventory inventory = movement.getInventory();
        ensureCoverage(inventory, movement.getStockBefore(), movement);

        int pending = Math.abs(movement.getQuantity());
        List<InventoryCostLayer> layers = layerRepository.findAvailableByInventoryIdForUpdate(inventory.getId());

        for (InventoryCostLayer layer : layers) {
            if (pending == 0) {
                break;
            }

            int quantity = Math.min(pending, layer.getQuantityRemaining());
            layer.setQuantityRemaining(layer.getQuantityRemaining() - quantity);
            allocationRepository.save(InventoryCostAllocation.builder()
                    .costLayer(layer)
                    .inventoryMovement(movement)
                    .quantity(quantity)
                    .build());
            pending -= quantity;
        }

        if (pending > 0) {
            throw new BusinessException("No se pudo asignar costo a todas las unidades retiradas del inventario");
        }
    }

    private void registerInbound(InventoryMovement movement, InventoryCostSourceType sourceType) {
        if (layerRepository.findBySourceMovementId(movement.getId()).isPresent()) {
            return;
        }

        Inventory inventory = movement.getInventory();
        ensureCoverage(inventory, movement.getStockBefore(), movement);
        createUnknownLayer(
                inventory,
                movement.getQuantity(),
                sourceType,
                movement,
                movement.getReferenceId()
        );
    }

    private boolean restoreSaleAllocations(InventoryMovement movement) {
        ensureCoverage(movement.getInventory(), movement.getStockBefore(), movement);

        List<InventoryCostAllocation> allocations = allocationRepository.findActiveByMovementReferenceForUpdate(
                movement.getInventory().getId(),
                InventoryMovementReferenceType.SALE,
                movement.getReferenceId()
        );

        if (allocations.isEmpty()) {
            return false;
        }

        int allocatedQuantity = allocations.stream().mapToInt(InventoryCostAllocation::getQuantity).sum();
        if (allocatedQuantity > movement.getQuantity()) {
            throw new BusinessException("La devolución parcial de una venta todavía no está soportada por el historial de costos");
        }

        allocations.forEach(allocation -> reverseAllocation(allocation, movement));

        int missing = movement.getQuantity() - allocatedQuantity;
        if (missing > 0) {
            createUnknownLayer(
                    movement.getInventory(),
                    missing,
                    InventoryCostSourceType.RETURN,
                    movement,
                    movement.getReferenceId()
            );
        }

        return true;
    }

    private void reverseAllocation(InventoryCostAllocation allocation, InventoryMovement reversalMovement) {
        InventoryCostLayer layer = allocation.getCostLayer();
        int restored = layer.getQuantityRemaining() + allocation.getQuantity();
        if (restored > layer.getQuantityReceived()) {
            throw new BusinessException("La reversión excede la cantidad original de la capa de costo");
        }

        layer.setQuantityRemaining(restored);
        allocation.setReversedAt(Instant.now());
        allocation.setReversedByMovement(reversalMovement);
    }

    private void ensureCoverage(Inventory inventory, int expectedStock, InventoryMovement movement) {
        long covered = layerRepository.sumRemainingByInventoryId(inventory.getId());
        if (covered == expectedStock) {
            return;
        }

        if (covered > expectedStock) {
            throw new BusinessException(
                    "El historial de costos del inventario es inconsistente con el stock actual. Inventario: " + inventory.getId()
            );
        }

        int missing = Math.toIntExact(expectedStock - covered);
        createUnknownLayer(
                inventory,
                missing,
                InventoryCostSourceType.UNTRACKED,
                null,
                movement != null && movement.getId() != null ? "AUTO-" + movement.getId() : null
        );
    }

    private InventoryCostLayer createUnknownLayer(
            Inventory inventory,
            int quantity,
            InventoryCostSourceType sourceType,
            InventoryMovement sourceMovement,
            String sourceReferenceId
    ) {
        if (quantity <= 0) {
            throw new BusinessException("La capa de costo debe contener al menos una unidad");
        }

        InventoryCostReferencePriceResolver.ReferencePriceSnapshot reference = referencePriceResolver.resolve(inventory);
        return layerRepository.save(InventoryCostLayer.builder()
                .inventory(inventory)
                .quantityReceived(quantity)
                .quantityRemaining(quantity)
                .costType(InventoryCostType.UNKNOWN)
                .referencePrice(reference.price())
                .referencePriceSource(reference.source())
                .sourceType(sourceType)
                .sourceMovement(sourceMovement)
                .sourceReferenceId(sourceReferenceId)
                .enteredAt(Instant.now())
                .build());
    }

    private boolean isSaleReturn(InventoryMovement movement) {
        return movement.getType() == InventoryMovementType.RETURN
                && movement.getReferenceType() == InventoryMovementReferenceType.SALE
                && movement.getReferenceId() != null;
    }

    private InventoryCostSourceType sourceType(InventoryMovement movement) {
        return switch (movement.getType()) {
            case PURCHASE -> InventoryCostSourceType.PURCHASE;
            case RETURN -> InventoryCostSourceType.RETURN;
            case ADJUSTMENT -> InventoryCostSourceType.ADJUSTMENT;
            case INITIAL_STOCK, ENTRY -> InventoryCostSourceType.STOCK_ENTRY;
            case SALE, DAMAGE, LOSS -> throw new IllegalArgumentException(
                    "Un movimiento de salida no puede crear una capa de costo"
            );
        };
    }

    private InventoryCostSourceType sourceType(InventoryCountPurpose purpose) {
        return switch (purpose) {
            case INITIAL_LOAD -> InventoryCostSourceType.OPENING_INVENTORY;
            case DELIVERY -> InventoryCostSourceType.STOCK_ENTRY;
            case RECONCILIATION, AUDIT -> InventoryCostSourceType.ADJUSTMENT;
        };
    }
}
