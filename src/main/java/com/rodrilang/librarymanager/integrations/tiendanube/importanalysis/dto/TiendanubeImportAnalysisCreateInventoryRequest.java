package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record TiendanubeImportAnalysisCreateInventoryRequest(
        @NotNull Long bookId,
        @NotNull @DecimalMin("0.01") BigDecimal salePrice,
        @NotNull @PositiveOrZero Integer initialStock,
        @NotNull @PositiveOrZero Integer minimumStock,
        Boolean syncStock
) {
    public boolean shouldSyncStock() {
        return Boolean.TRUE.equals(syncStock);
    }
}
