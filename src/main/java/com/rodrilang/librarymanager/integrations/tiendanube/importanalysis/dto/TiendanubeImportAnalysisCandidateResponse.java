package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisCandidateSource;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisMatchType;

import java.math.BigDecimal;

public record TiendanubeImportAnalysisCandidateResponse(
        Long inventoryId,
        Long bookId,
        String isbn,
        String title,
        String authors,
        String publisher,
        String coverUrl,
        BookCondition condition,
        Integer stock,
        BigDecimal salePrice,
        int rank,
        double score,
        TiendanubeImportAnalysisCandidateSource source,
        TiendanubeImportAnalysisMatchType matchType,
        boolean availableForLink
) {
}
