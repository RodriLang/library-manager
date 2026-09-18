package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto;

public record TiendanubeImportAnalysisBulkResolveRequest(
        Boolean syncStock
) {
    public boolean shouldSyncStock() {
        return Boolean.TRUE.equals(syncStock);
    }
}
