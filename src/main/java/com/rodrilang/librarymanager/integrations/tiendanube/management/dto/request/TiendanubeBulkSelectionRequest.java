package com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request;

import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkSelectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record TiendanubeBulkSelectionRequest(
        @NotNull TiendanubeBulkSelectionType type,
        Set<Long> inventoryIds,
        @Valid TiendanubeInventoryFilterRequest filter
) {
}
