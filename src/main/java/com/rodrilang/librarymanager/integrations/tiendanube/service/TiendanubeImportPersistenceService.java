package com.rodrilang.librarymanager.integrations.tiendanube.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImportResultResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;

public interface TiendanubeImportPersistenceService {

    TiendanubeImportResultResponse importExistingBook(
            Long bookstoreId,
            Long bookId,
            Long storeId,
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant
    );
}
