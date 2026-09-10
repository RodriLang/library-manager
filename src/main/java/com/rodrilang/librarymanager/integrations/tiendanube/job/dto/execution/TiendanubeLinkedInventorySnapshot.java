package com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateVariantRequest;

import java.math.BigDecimal;

public record TiendanubeLinkedInventorySnapshot(
        Long inventoryId,
        Long linkId,
        Long storeId,
        Long productId,
        Long variantId,
        Integer stock,
        BigDecimal salePrice,
        String sku,
        String resolvedSku,
        TiendanubeUpdateVariantRequest fullVariantRequest
) {
}
