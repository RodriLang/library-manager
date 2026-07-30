package com.rodrilang.librarymanager.integrations.tiendanube.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TiendanubeUpdateVariantRequest(
        String sku,
        String barcode,
        BigDecimal price,
        Integer stock,
        Boolean stockManagement
) {
}
