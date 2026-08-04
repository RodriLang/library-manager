package com.rodrilang.librarymanager.isbn.service;

import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CanonicalIsbnResolver {

    private final IsbnService isbnService;

    public String resolve(String value) {
        ParsedIsbn parsedIsbn = isbnService.parse(value);
        return parsedIsbn.valid() ? parsedIsbn.isbn13() : null;
    }
}