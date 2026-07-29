package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import java.math.BigDecimal;

public record TiendanubeVariantResponse(

        Long id,

        String sku,

        String barcode,

        BigDecimal price,

        Integer stock

) {
}