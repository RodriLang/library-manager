package com.rodrilang.librarymanager.importer.price.dto;

import com.rodrilang.librarymanager.model.Book;

public record IsbnBookConflict(
        String canonicalIsbn,
        String isbn10,
        Book bookByIsbn13,
        Book bookByIsbn10
) {
}