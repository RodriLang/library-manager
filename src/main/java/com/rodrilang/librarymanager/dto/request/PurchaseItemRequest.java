package com.rodrilang.librarymanager.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PurchaseItemRequest(

        @NotNull
        Long bookId,

        @NotNull
        @Min(1)
        Integer quantity,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal costPrice,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal salePrice,

        @Min(0)
        Integer minimumStock

) {
}