package com.rodrilang.librarymanager.integrations.tiendanube.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;

public interface TiendanubeProductLinkPersistenceService {

    void savePublishedLink(
            Long inventoryId,
            Long storeId,
            Long productId,
            TiendanubeVariantResponse variant
    );
}