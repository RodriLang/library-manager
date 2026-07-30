package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;

import java.time.Instant;

public record TiendanubeInventoryStatusResponse(
        Long inventoryId,
        TiendanubeInventoryStatus status,
        Long productId,
        Long variantId,
        String sku,
        Instant lastSyncedAt,
        String lastError
) {
}