package com.rodrilang.librarymanager.integrations.tiendanube.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
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