package com.rodrilang.librarymanager.purchasing.service;

import com.rodrilang.librarymanager.editorialprice.service.EffectiveEditorialPriceService;
import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.inventory.cost.service.InventoryCostMovementService;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeCommand;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeResult;
import com.rodrilang.librarymanager.inventory.movement.service.InventoryStockService;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.purchasing.model.PurchaseItem;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PurchaseInventoryService {
    private final InventoryRepository inventoryRepository;
    private final InventoryStockService inventoryStockService;
    private final InventoryCostMovementService inventoryCostMovementService;
    private final EffectiveEditorialPriceService editorialPriceService;

    public void receive(PurchaseItem item) {
        Inventory inventory = findOrCreate(item);
        InventoryStockChangeResult result = inventoryStockService.changeStock(
                inventory.getId(),
                new InventoryStockChangeCommand(
                        item.getQuantity(),
                        InventoryMovementType.PURCHASE,
                        InventoryMovementSource.MANUAL,
                        InventoryMovementReferenceType.PURCHASE,
                        item.getPurchase().getId().toString(),
                        "Ingreso por compra #" + item.getPurchase().getId()
                )
        );

        inventoryCostMovementService.confirmPurchaseCost(
                result.movement(),
                item,
                item.getUnitCost(),
                item.getDiscountPercentage(),
                item.getEditorialPriceSnapshot()
        );
    }

    private Inventory findOrCreate(PurchaseItem item) {
        return inventoryRepository.findByBookIdAndBookstoreIdAndCondition(
                        item.getBook().getId(),
                        item.getPurchase().getBookstore().getId(),
                        item.getCondition()
                )
                .map(this::reactivate)
                .orElseGet(() -> create(item));
    }

    private Inventory reactivate(Inventory inventory) {
        if (!Boolean.TRUE.equals(inventory.getActive())) inventory.setActive(true);
        return inventory;
    }

    private Inventory create(PurchaseItem item) {
        BigDecimal salePrice = editorialPriceService.findCurrentByBookId(item.getBook().getId())
                .map(price -> price.getPrice())
                .orElse(item.getEditorialPriceSnapshot());

        if (salePrice == null) {
            throw new BusinessException(
                    "El libro '" + item.getBook().getTitle() + "' necesita un precio editorial o precio de referencia antes de confirmar la compra"
            );
        }

        return inventoryRepository.save(Inventory.builder()
                .book(item.getBook())
                .bookstore(item.getPurchase().getBookstore())
                .condition(item.getCondition())
                .salePrice(salePrice)
                .stock(0)
                .minimumStock(0)
                .tiendanubeStatus(TiendanubeInventoryStatus.NOT_PUBLISHED)
                .tiendanubePriceSyncEnabled(false)
                .editorialPriceSyncEnabled(false)
                .active(true)
                .build());
    }
}
