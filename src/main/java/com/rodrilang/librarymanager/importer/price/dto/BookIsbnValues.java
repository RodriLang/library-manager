package com.rodrilang.librarymanager.importer.price.dto;

public record BookIsbnValues(
        String legacyIsbn,
        String isbn10,
        String isbn13
) {
}