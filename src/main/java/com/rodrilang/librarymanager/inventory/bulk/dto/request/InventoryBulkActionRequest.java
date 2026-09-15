package com.rodrilang.librarymanager.inventory.bulk.dto.request;

import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InventoryBulkActionRequest(

        @NotNull
        @Valid
        InventoryBulkSelectionRequest selection,

        @NotNull
        InventoryBulkAction action,

        @Min(0)
        Integer minimumStock

) {
}