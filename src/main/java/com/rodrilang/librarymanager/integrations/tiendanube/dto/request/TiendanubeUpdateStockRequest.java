package com.rodrilang.librarymanager.integrations.tiendanube.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TiendanubeUpdateStockRequest(

        @JsonProperty("stock_management")
        Boolean stock_management,

        Integer stock
) {
}