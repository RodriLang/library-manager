package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import java.util.List;

public record TiendanubeProductsPage(
        List<TiendanubeProductResponse> products,
        long total,
        int page,
        int size,
        int totalPages
) {
}
