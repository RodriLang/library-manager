package com.rodrilang.librarymanager.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InventorySaleRequest(

        @NotNull
        @Positive
        Integer quantity,

        @NotNull
        Boolean replenish

) {
}