package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model;

import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisItemStatus;

public record TiendanubeImportAnalysisItemLock(
        Long id,
        Long runId,
        Long bookstoreId,
        Long tiendanubeStoreId,
        Long storeId,
        Long productId,
        Long variantId,
        String remoteSku,
        TiendanubeImportAnalysisItemStatus status,
        Long suggestedInventoryId
) {
}
