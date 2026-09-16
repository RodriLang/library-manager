package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto;

public record TiendanubeImportAnalysisBulkFailureResponse(
        Long itemId,
        String message
) {
}
