package com.rodrilang.librarymanager.integrations.tiendanube.dto.request;

import jakarta.validation.constraints.NotNull;

public record ImportTiendanubeProductRequest(
        @NotNull Long productId,
        @NotNull Long variantId
) {
}