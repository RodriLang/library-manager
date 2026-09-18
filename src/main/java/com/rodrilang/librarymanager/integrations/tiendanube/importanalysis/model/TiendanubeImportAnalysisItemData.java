package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model;

import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisItemStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisMatchType;

import java.math.BigDecimal;
import java.util.List;

public record TiendanubeImportAnalysisItemData(
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
        String message,
        List<TiendanubeImportAnalysisCandidateData> candidates
) {
}
