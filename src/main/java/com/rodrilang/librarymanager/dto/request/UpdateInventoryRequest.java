package com.rodrilang.librarymanager.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record UpdateInventoryRequest(

        @DecimalMin("0.00")
        BigDecimal costPrice,

        @DecimalMin("0.00")
        BigDecimal salePrice,

        @Min(0)
        Integer minimumStock,

        Boolean active

) {
}