package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import java.util.List;

public record TiendanubeImportPreviewResponse(
        long total,
        long pageReadyToImport,
        long pageReadyToLink,
        long pageRequiresReview,
        long pageAlreadyLinked,
        int page,
        int size,
        int totalPages,
        List<TiendanubeImportPreviewItemResponse> items
) {
}