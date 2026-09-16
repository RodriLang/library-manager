package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model;

import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisMatchType;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;

public record TiendanubeImportAnalysisParsedIdentifier(
        ParsedIsbn parsed,
        String source,
        TiendanubeImportAnalysisMatchType matchType
) {
}
