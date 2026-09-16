package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model;

import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisCandidateSource;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisMatchType;

public record TiendanubeImportAnalysisCandidateData(
        Long inventoryId,
        Long bookId,
        int rank,
        TiendanubeImportAnalysisCandidateSource source,
        TiendanubeImportAnalysisMatchType matchType,
        double score,
        boolean availableForLink
) {
}
