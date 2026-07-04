package com.rodrilang.librarymanager.dto.request;

import com.rodrilang.librarymanager.enums.BookCondition;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UpdateInventoryRequest(

        @Positive
        BigDecimal salePrice,

        @Min(0)
        Integer minimumStock,

        Boolean active,

        BookCondition condition

) {
}