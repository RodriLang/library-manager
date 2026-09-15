package com.rodrilang.librarymanager.inventory.count.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddInventoryCountBookRequest(

        @NotNull
        Long bookId,

        @NotNull
        @Min(1)
        Integer quantity

) {
}