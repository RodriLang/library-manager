package com.rodrilang.librarymanager.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InventoryQuantityRequest(

        @NotNull
        @Min(1)
        Integer quantity

) {
}