package com.rodrilang.librarymanager.importer.price.dto.internal;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.dto.IsbnBookConflict;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Publisher;

import java.util.Map;
import java.util.Set;

public record ImportContext(
        PriceListProvider provider,
        Map<String, Book> booksByIsbn13,
        Map<String, Book> booksByIsbn10,
        Map<String, Book> booksByCanonicalIsbn,
        Map<String, Book> booksByExternalCode,
        Map<String, IsbnBookConflict> isbnConflicts,
        Set<Long> bookIdsWithAuthors,
        Map<String, Publisher> publishersByName,
        Map<String, Author> authorsByName
) {
}