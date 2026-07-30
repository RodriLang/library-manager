package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeImportAction;

public record TiendanubeImportItemResultResponse(
        Long productId,
        Long variantId,
        Long bookId,
        Long inventoryId,
        boolean success,
        TiendanubeImportAction action,
        String error
) {
}