package com.rodrilang.librarymanager.purchasing.dto.request;

import com.rodrilang.librarymanager.enums.BookCondition;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpsertPurchaseItemRequest(
        @NotNull Long bookId,
        BookCondition condition,
        @NotNull @Min(1) Integer quantity,
        @DecimalMin("0.00") BigDecimal editorialPriceSnapshot,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal discountPercentage,
        @DecimalMin(value = "0.00", inclusive = false) BigDecimal unitCost
) {
}
