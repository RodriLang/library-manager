package com.rodrilang.librarymanager.importer.price.dto;

import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Publisher;

import java.util.Map;

public record ImportContext(
        Map<String, Book> booksByIsbn13,
        Map<String, Book> booksByIsbn10,
        Map<String, Book> booksByCanonicalIsbn,
        Map<String, IsbnBookConflict> isbnConflicts,
        Map<String, Publisher> publishersByName,
        Map<String, Author> authorsByName
) {
}