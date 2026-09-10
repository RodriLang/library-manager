package com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution;

public record TiendanubeDeletePublicationSnapshot(
        Long inventoryId,
        Long linkId,
        Long storeId,
        Long productId
) {
}
