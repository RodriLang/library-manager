package com.rodrilang.librarymanager.dto.request;

import com.rodrilang.librarymanager.enums.BookCondition;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.NonNull;

import java.math.BigDecimal;

public record AddBookToInventoryRequest(

        @NonNull
        @Positive
        BigDecimal salePrice,

        @Min(0)
        Integer initialStock,

        @Min(0)
        Integer minimumStock,

        BookCondition condition

) {
}
