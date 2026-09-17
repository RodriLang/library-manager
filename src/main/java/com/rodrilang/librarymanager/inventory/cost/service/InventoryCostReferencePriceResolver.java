package com.rodrilang.librarymanager.inventory.cost.service;

import com.rodrilang.librarymanager.editorialprice.service.EffectiveEditorialPriceService;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostReferencePriceSource;
import com.rodrilang.librarymanager.model.Inventory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InventoryCostReferencePriceResolver {

    private final EffectiveEditorialPriceService editorialPriceService;

    public ReferencePriceSnapshot resolve(Inventory inventory) {
        return editorialPriceService.findCurrentByBookId(inventory.getBook().getId())
                .map(price -> new ReferencePriceSnapshot(
                        price.getPrice(),
                        InventoryCostReferencePriceSource.EDITORIAL_PRICE
                ))
                .orElseGet(() -> inventory.getSalePrice() != null
                        ? new ReferencePriceSnapshot(
                                inventory.getSalePrice(),
                                InventoryCostReferencePriceSource.INVENTORY_SALE_PRICE
                        )
                        : new ReferencePriceSnapshot(null, null));
    }

    public record ReferencePriceSnapshot(
            BigDecimal price,
            InventoryCostReferencePriceSource source
    ) {
    }
}
