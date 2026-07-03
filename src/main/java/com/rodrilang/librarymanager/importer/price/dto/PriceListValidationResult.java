package com.rodrilang.librarymanager.importer.price.dto;

import java.util.List;

public record PriceListValidationResult(

        List<PriceListRow> validRows,

        List<PriceListImportError> errors

) {
}
