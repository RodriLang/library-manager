package com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response;

public record TiendanubeManagementSummaryResponse(
        long total,
        long published,
        long notPublished,
        long pending,
        long errors,
        long remoteNotFound,
        long priceSyncEnabled
) {
}
