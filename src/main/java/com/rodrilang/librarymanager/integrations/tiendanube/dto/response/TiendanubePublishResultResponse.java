package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;

public record TiendanubePublishResultResponse(
        Long inventoryId,
        Long productId,
        Long variantId,
        TiendanubeInventoryStatus status
) {
}