package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;

public record TiendanubeRetryResponse(
        Long inventoryId,
        TiendanubeInventoryStatus status,
        String action
) {
}