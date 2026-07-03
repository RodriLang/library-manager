package com.rodrilang.librarymanager.importer.price.dto;

import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobStatus;

public record PriceListImportStartResponse(
        Long jobId,
        PriceListImportJobStatus status,
        String message
) {
}