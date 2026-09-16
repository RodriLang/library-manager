package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model;

import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisMatchType;
import com.rodrilang.librarymanager.model.Inventory;

public record TiendanubeImportAnalysisScoredInventory(
        Inventory inventory,
        double titleScore,
        double score,
        TiendanubeImportAnalysisMatchType matchType
) {
}
