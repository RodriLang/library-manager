package com.rodrilang.librarymanager.integrations.tiendanube.dto.request;

import jakarta.validation.constraints.NotNull;

public record LinkTiendanubeProductRequest(

        @NotNull
        Long inventoryId,

        @NotNull
        Long productId,

        @NotNull
        Long variantId

) {
}