package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeImportMatchType;

import java.math.BigDecimal;
import java.util.List;

public record TiendanubeImportPreviewItemResponse(
        Long productId,
        Long variantId,
        String remoteName,
        String imageUrl,
        String sku,
        String barcode,
        BigDecimal remotePrice,
        Integer remoteStock,
        TiendanubeImportMatchType matchType,
        Long matchedBookId,
        Long existingInventoryId,
        boolean selectedByDefault,
        boolean requiresReview,
        List<TiendanubeImportBookCandidateResponse> candidates
) {
}