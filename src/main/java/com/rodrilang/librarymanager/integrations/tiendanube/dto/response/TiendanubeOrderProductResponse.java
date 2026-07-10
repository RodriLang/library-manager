package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TiendanubeOrderProductResponse(

        @JsonProperty("product_id")
        Long productId,

        @JsonProperty("variant_id")
        Long variantId,

        String sku,

        Integer quantity,

        String name
) {
}