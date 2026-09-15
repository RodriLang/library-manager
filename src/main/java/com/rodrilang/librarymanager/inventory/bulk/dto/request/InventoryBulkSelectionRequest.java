package com.rodrilang.librarymanager.inventory.bulk.dto.request;

import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkSelectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Set;

public record InventoryBulkSelectionRequest(

        @NotNull
        InventoryBulkSelectionType type,

        Set<@Positive Long> inventoryIds,

        Set<@Positive Long> excludedInventoryIds,

        @Valid
        InventoryBulkFilterRequest filter

) {
}