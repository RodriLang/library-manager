package com.rodrilang.librarymanager.inventory.cost.service;

import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostReferencePriceSource;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceService;
import com.rodrilang.librarymanager.model.Inventory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InventoryCostReferencePriceResolver {

    private final InventoryPriceService inventoryPriceService;

    public ReferencePriceSnapshot resolve(Inventory inventory) {
        BigDecimal price = inventoryPriceService.currentAmount(inventory.getId());
        return price != null
                ? new ReferencePriceSnapshot(price, InventoryCostReferencePriceSource.INVENTORY_PRICE)
                : new ReferencePriceSnapshot(null, null);
    }

    public record ReferencePriceSnapshot(BigDecimal price, InventoryCostReferencePriceSource source) {}
}
