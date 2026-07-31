package com.rodrilang.librarymanager.integrations.tiendanube.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeBulkImportRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeBulkImportResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImportPreviewResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImportResultResponse;

public interface TiendanubeImportService {

    TiendanubeImportPreviewResponse preview(
            Long bookstoreId,
            int page,
            int size
    );

    TiendanubeImportResultResponse importProduct(Long bookstoreId, Long productId, Long variantId);

    TiendanubeBulkImportResponse importProducts(Long bookstoreId, TiendanubeBulkImportRequest request);
}