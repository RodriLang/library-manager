package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto;

import java.util.UUID;

public record TiendanubeClaimedReconciliationRun(
        Long id,
        Long bookstoreId,
        Long tiendanubeStoreId,
        Long storeId,
        UUID processingToken
) {
}
