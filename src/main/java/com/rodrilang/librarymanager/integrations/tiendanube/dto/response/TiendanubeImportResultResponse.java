package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;

public record TiendanubeImportResultResponse(
        Long inventoryId,
        Long bookId,
        Long productId,
        Long variantId,
        TiendanubeInventoryStatus status,
        boolean bookCreated
) {
}