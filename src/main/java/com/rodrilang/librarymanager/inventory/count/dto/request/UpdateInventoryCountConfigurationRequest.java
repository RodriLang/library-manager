package com.rodrilang.librarymanager.inventory.count.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateInventoryCountConfigurationRequest(
        Boolean publishOnTiendanube,
        Boolean tiendanubePriceSyncEnabled,
        @PositiveOrZero Integer minimumStock,
        Boolean consignment,
        @Positive Long consignmentProviderId,
        boolean applyToAll
) {
    public UpdateInventoryCountConfigurationRequest(Boolean publishOnTiendanube, Boolean tiendanubePriceSyncEnabled, Integer minimumStock, boolean applyToAll) {
        this(publishOnTiendanube, tiendanubePriceSyncEnabled, minimumStock, null, null, applyToAll);
    }
}
