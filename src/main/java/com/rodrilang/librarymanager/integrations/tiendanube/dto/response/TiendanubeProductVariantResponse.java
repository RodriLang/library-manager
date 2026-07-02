package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TiendanubeProductVariantResponse(

        Long id,

        @JsonProperty("product_id")
        Long productId,

        String sku,

        Integer stock,

        @JsonProperty("stock_management")
        Boolean stockManagement
) {
}