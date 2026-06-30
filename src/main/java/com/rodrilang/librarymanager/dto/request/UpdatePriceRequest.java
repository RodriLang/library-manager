package com.rodrilang.librarymanager.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdatePriceRequest(

        @NotNull
        @DecimalMin("0.0")
        BigDecimal salePrice

) {
}