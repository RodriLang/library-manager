package com.rodrilang.librarymanager.integrations.tiendanube.dto.request;

import java.math.BigDecimal;

public record TiendanubeCreateVariantRequest(

        BigDecimal price,

        Integer stock,

        String sku,

        String barcode,

        BigDecimal weight,

        BigDecimal width,

        BigDecimal height,

        BigDecimal depth

) {
}