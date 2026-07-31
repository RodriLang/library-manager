package com.rodrilang.librarymanager.integrations.tiendanube.dto.request;

import com.rodrilang.librarymanager.enums.BookCondition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record TiendanubeImportItemRequest(
        @NotNull Long productId,
        @NotNull Long variantId,
        @NotNull Long bookId,
        @NotNull BookCondition condition,
        @NotNull @PositiveOrZero Integer stock,
        @NotNull @PositiveOrZero BigDecimal salePrice,
        Boolean syncPrice,
        Boolean editorialPriceSyncEnabled
) {
}