package com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateProductRequest;

public record TiendanubePublicationSnapshot(
        TiendanubeLinkedInventorySnapshot linkedInventory,
        TiendanubeUpdateProductRequest productRequest,
        String coverUrl,
        String lastSyncedCoverUrl
) {
}
