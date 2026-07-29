package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;

public record TiendanubeProductLinkResponse(
        Long inventoryId,
        Long productId,
        Long variantId,
        String sku,
        TiendanubeInventoryStatus status
) {
}