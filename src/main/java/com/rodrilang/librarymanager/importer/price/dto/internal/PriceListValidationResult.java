package com.rodrilang.librarymanager.importer.price.dto.internal;

import com.rodrilang.librarymanager.importer.price.dto.PriceListImportError;

import java.util.List;

public record PriceListValidationResult(

        List<PriceListRow> validRows,

        List<PriceListImportError> errors

) {
}
