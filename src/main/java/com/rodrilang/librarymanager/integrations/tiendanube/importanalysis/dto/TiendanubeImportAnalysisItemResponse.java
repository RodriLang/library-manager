package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto;

import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisItemStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisMatchType;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisResolutionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TiendanubeImportAnalysisItemResponse(
        Long id,
        Long productId,
        Long variantId,
        String remoteName,
        String remoteSku,
        String remoteBarcode,
        String remoteIsbn,
        String identifierSource,
        boolean identifierRecovered,
        BigDecimal remotePrice,
        Integer remoteStock,
        String remoteImageUrl,
        Boolean remotePublished,
        TiendanubeImportAnalysisItemStatus status,
        TiendanubeImportAnalysisMatchType matchType,
        Long suggestedInventoryId,
        Long suggestedBookId,
        Long resolvedInventoryId,
        TiendanubeImportAnalysisResolutionType resolutionType,
        String message,
        Instant resolvedAt,
        List<TiendanubeImportAnalysisCandidateResponse> candidates
) {
}
