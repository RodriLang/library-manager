package com.rodrilang.librarymanager.catalog.contribution.service;

import com.rodrilang.librarymanager.catalog.contribution.enums.BookField;
import com.rodrilang.librarymanager.catalog.contribution.model.BookstoreBookFieldOverride;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Publisher;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public record BookstoreBookEffectiveValues(
        String title,
        String subtitle,
        String description,
        String language,
        Integer pageCount,
        Integer publicationYear,
        Integer publicationMonth,
        String coverUrl,
        String categoryName,
        String genreName,
        Publisher publisher,
        Set<Author> authors,
        BigDecimal weightGrams,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal depthCm,
        Map<BookField, BookstoreBookFieldOverride> overrides
) {
    public boolean isOverridden(BookField field) {
        return overrides != null && overrides.containsKey(field);
    }
}
