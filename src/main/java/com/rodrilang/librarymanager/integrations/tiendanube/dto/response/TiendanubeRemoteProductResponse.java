package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import java.util.List;

public record TiendanubeRemoteProductResponse(

        Long productId,

        String name,

        String imageUrl,

        Boolean published,

        List<TiendanubeRemoteVariantResponse> variants
) {
}