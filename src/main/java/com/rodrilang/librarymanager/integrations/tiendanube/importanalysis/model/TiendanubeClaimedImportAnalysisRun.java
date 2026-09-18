package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model;

import java.util.UUID;

public record TiendanubeClaimedImportAnalysisRun(
        Long id,
        Long bookstoreId,
        Long tiendanubeStoreId,
        Long storeId,
        UUID processingToken
) {
}
