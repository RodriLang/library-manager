package com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateProductRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateVariantRequest;

public record TiendanubePublishSnapshot(
        Long inventoryId,
        Long bookstoreId,
        Long storeId,
        String isbn,
        String titleSearch,
        String coverUrl,
        TiendanubeCreateProductRequest productRequest,
        TiendanubeUpdateVariantRequest variantRequest
) {
}
