package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record TiendanubeImportAnalysisCreateInventoryRequest(
        @NotNull Long bookId,
        @DecimalMin("0.01") BigDecimal salePrice,
        @NotNull @PositiveOrZero Integer initialStock,
        @NotNull @PositiveOrZero Integer minimumStock,
        Boolean syncStock,
        com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceSource priceSource
) {
    public com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceSource resolvedPriceSource() {
        var source = priceSource == null
                ? com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceSource.TIENDANUBE_IMPORT
                : priceSource;
        if (source != com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceSource.MANUAL
                && source != com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceSource.PRICE_LIST
                && source != com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceSource.TIENDANUBE_IMPORT) {
            throw new IllegalArgumentException("Origen de precio no permitido para la vinculación de Tiendanube");
        }
        return source;
    }
    public boolean shouldSyncStock() {
        return Boolean.TRUE.equals(syncStock);
    }
}
