package com.rodrilang.librarymanager.integrations.tiendanube.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductLinkResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubePublishResultResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeRemoteProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeRetryResponse;

import java.util.List;

public interface TiendanubeProductService {

    TiendanubePublishResultResponse publishInventory(Long inventoryId);

    List<TiendanubeRemoteProductResponse> getRemoteProducts(Long bookstoreId);

    TiendanubeProductLinkResponse linkExistingProduct(Long inventoryId, Long productId, Long variantId);

    TiendanubeRetryResponse retry(Long inventoryId);
}
