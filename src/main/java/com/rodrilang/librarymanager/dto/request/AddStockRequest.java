package com.rodrilang.librarymanager.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AddStockRequest(

        @NotNull
        Long bookId,

        @NotNull
        @Min(1)
        Integer quantity,

        BigDecimal salePrice,

        BigDecimal costPrice

) {
}