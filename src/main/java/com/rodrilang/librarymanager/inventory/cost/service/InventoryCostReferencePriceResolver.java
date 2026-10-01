package com.rodrilang.librarymanager.inventory.cost.service;

import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostReferencePriceSource;
import com.rodrilang.librarymanager.model.Inventory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class InventoryCostReferencePriceResolver {

    public ReferencePriceSnapshot resolve(Inventory inventory) {
        return inventory.getSalePrice() != null
                ? new ReferencePriceSnapshot(
                        inventory.getSalePrice(),
                        InventoryCostReferencePriceSource.INVENTORY_SALE_PRICE
                )
                : new ReferencePriceSnapshot(null, null);
    }

    public record ReferencePriceSnapshot(
            BigDecimal price,
            InventoryCostReferencePriceSource source
    ) {
    }
}
