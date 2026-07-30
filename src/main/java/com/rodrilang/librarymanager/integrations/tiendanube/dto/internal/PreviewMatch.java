package com.rodrilang.librarymanager.integrations.tiendanube.dto.internal;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImportBookCandidateResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeImportMatchType;

import java.util.List;

public record PreviewMatch(
        TiendanubeImportMatchType matchType,
        Long bookId,
        Long inventoryId,
        boolean selectedByDefault,
        boolean requiresReview,
        List<TiendanubeImportBookCandidateResponse> candidates
) {
}