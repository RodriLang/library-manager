package com.rodrilang.librarymanager.inventory.count.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record UpdateInventoryCountItemRequest(
        @Positive Integer quantity,
        @Positive BigDecimal salePrice,
        Boolean editorialPriceSyncEnabled,
        Boolean publishOnTiendanube,
        Boolean tiendanubePriceSyncEnabled,
        @PositiveOrZero Integer minimumStock
) {
}
