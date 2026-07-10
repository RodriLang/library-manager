package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import java.util.List;

public record TiendanubeOrderResponse(

        Long id,

        List<TiendanubeOrderProductResponse> products
) {
}