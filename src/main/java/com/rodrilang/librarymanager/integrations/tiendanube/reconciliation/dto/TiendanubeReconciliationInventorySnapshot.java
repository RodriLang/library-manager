package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto;

import java.math.BigDecimal;

public record TiendanubeReconciliationInventorySnapshot(
        Long inventoryId,
        Long linkId,
        Long productId,
        Long variantId,
        Integer localStock,
        BigDecimal localPrice,
        boolean priceSyncEnabled
) {
}
