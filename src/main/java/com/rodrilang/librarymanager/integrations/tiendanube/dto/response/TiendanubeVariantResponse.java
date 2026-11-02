package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import java.math.BigDecimal;

public record TiendanubeVariantResponse(

        Long id,

        String sku,

        String barcode,

        BigDecimal price,

        Integer stock,
        BigDecimal weight, BigDecimal width, BigDecimal height, BigDecimal depth

) {
    public TiendanubeVariantResponse(Long id, String sku, String barcode, BigDecimal price, Integer stock) {
        this(id, sku, barcode, price, stock, null, null, null, null);
    }
}