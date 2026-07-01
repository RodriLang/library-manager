package com.rodrilang.librarymanager.importer.price.dto;

public record PriceListImportError(

        int rowNumber,

        String isbn,

        String message

) {
}