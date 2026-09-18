package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto;

import jakarta.validation.constraints.NotNull;

public record TiendanubeImportAnalysisResolveRequest(
        @NotNull Long inventoryId,
        Boolean syncStock
) {
    public boolean shouldSyncStock() {
        return Boolean.TRUE.equals(syncStock);
    }
}
