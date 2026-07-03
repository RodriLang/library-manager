package com.rodrilang.librarymanager.importer.price.dto;

import com.rodrilang.librarymanager.enums.RowValidationSeverity;

public record PriceListImportError(

        int rowNumber,

        String isbn,

        String message,

        RowValidationSeverity severity

) {
}