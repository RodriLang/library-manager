package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.MatchStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeMatchType;

import java.math.BigDecimal;

public record TiendanubeProductMatchResponse(

        Long productId,

        Long variantId,

        String name,

        String sku,

        String barcode,

        Integer remoteStock,

        MatchStatus status,

        InventoryMatchCandidateResponse candidate
) {
}