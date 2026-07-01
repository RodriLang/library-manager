package com.rodrilang.librarymanager.importer.price.dto;

import java.util.List;

public record PriceListImportResult(

        int totalRows,

        int createdBooks,

        int updatedBooks,

        int skippedRows,

        List<PriceListImportError> errors

) {
}