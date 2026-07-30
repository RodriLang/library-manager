package com.rodrilang.librarymanager.integrations.tiendanube.dto.internal;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeMatchType;

public record RemoteInventoryMatch(
        Long productId,
        Long variantId,
        TiendanubeMatchType matchType
) {

    public boolean autoLink() {
        return matchType == TiendanubeMatchType.EXACT_BARCODE
                || matchType == TiendanubeMatchType.EXACT_SKU;
    }
}