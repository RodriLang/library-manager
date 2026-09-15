package com.rodrilang.librarymanager.sales.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateSaleItemRequest(

        @NotNull
        Long inventoryId,

        @NotNull
        @Positive
        Integer quantity,

        Boolean replenish

) {
}
