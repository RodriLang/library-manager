package com.rodrilang.librarymanager.dto.request;

import com.rodrilang.librarymanager.enums.BookCondition;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ReactivateInventoryRequest(
        @Min(0) Integer stock,
        @Positive BigDecimal salePrice,
        @Min(0) Integer minimumStock,
        BookCondition condition,
        Boolean editorialPriceSyncEnabled,
        Boolean publishOnTiendanube,
        Boolean tiendanubePriceSyncEnabled
) {
}