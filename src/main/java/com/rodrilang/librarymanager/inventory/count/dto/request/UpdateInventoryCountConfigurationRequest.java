package com.rodrilang.librarymanager.inventory.count.dto.request;

import jakarta.validation.constraints.PositiveOrZero;

public record UpdateInventoryCountConfigurationRequest(
        Boolean editorialPriceSyncEnabled,
        Boolean publishOnTiendanube,
        Boolean tiendanubePriceSyncEnabled,
        @PositiveOrZero Integer minimumStock,
        boolean applyToAll
) {
}
