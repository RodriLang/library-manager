package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeMatchType;

import java.math.BigDecimal;
import java.util.List;

public record TiendanubeRemoteVariantResponse(

        Long variantId,

        String sku,

        String barcode,

        BigDecimal price,

        Integer stock,

        TiendanubeMatchType matchType,

        Long linkedInventoryId,

        List<InventoryMatchCandidateResponse> candidates

) {
}