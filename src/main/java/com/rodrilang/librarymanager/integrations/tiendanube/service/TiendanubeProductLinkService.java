package com.rodrilang.librarymanager.integrations.tiendanube.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductLinkResponse;

public interface TiendanubeProductLinkService {

    TiendanubeProductLinkResponse linkExistingProduct(
            Long inventoryId,
            Long productId,
            Long variantId
    );
}
