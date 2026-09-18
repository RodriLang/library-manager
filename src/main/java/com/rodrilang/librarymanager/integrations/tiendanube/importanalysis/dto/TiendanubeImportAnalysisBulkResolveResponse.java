package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto;

import java.util.List;

public record TiendanubeImportAnalysisBulkResolveResponse(
        int requested,
        int linked,
        int failed,
        List<TiendanubeImportAnalysisBulkFailureResponse> failures
) {
}
