package com.rodrilang.librarymanager.importer.price.dto;

import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Publisher;

import java.util.Map;

public record ImportContext(
        Map<String, Book> booksByIsbn,
        Map<String, Publisher> publishersByName,
        Map<String, Author> authorsByName
) {
}