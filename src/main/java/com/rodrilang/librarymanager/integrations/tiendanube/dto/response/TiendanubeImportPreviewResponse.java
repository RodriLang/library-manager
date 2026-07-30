package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import java.util.List;

public record TiendanubeImportPreviewResponse(
        int total,
        int readyToImport,
        int readyToLink,
        int requiresReview,
        int alreadyLinked,
        List<TiendanubeImportPreviewItemResponse> items
) {
}