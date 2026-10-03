package com.rodrilang.librarymanager.inventory.pricing.dto;

import jakarta.validation.constraints.NotNull;

public record ResolveInventoryPriceImportDuplicateRequest(
        @NotNull Long selectedItemId
) {
}