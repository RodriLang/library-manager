package com.rodrilang.librarymanager.inventory.count.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record UpdateInventoryCountItemRequest(
        @Positive Integer quantity,
        @Positive BigDecimal salePrice,
        Boolean publishOnTiendanube,
        Boolean tiendanubePriceSyncEnabled,
        @PositiveOrZero Integer minimumStock,
        @PositiveOrZero Integer consignmentQuantity,
        @Positive Long consignmentProviderId
) {
    public UpdateInventoryCountItemRequest(Integer quantity, BigDecimal salePrice, Boolean publishOnTiendanube, Boolean tiendanubePriceSyncEnabled, Integer minimumStock) {
        this(quantity, salePrice, publishOnTiendanube, tiendanubePriceSyncEnabled, minimumStock, null, null);
    }
}
