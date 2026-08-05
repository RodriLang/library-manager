package com.rodrilang.librarymanager.importer.price.dto.response;

import com.rodrilang.librarymanager.enums.RowValidationSeverity;

public record PriceListImportJobErrorResponse(
        int rowNumber,
        String isbn,
        String message,
        RowValidationSeverity severity
) {
}