package com.rodrilang.librarymanager.dto.response;

import com.rodrilang.librarymanager.enums.BookSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

public record BookDetailResponse(

        Long id,

        String isbn,

        String title,

        String subtitle,

        String description,

        String language,

        Integer pageCount,

        LocalDate publicationDate,

        String coverUrl,

        String categoryName,

        String genreName,

        BookSource source,

        Boolean active,

        PublisherResponse publisher,

        Set<AuthorResponse> authors,

        EditorialPriceResponse editorialPrice,

        LocalDate editorialPriceValidFrom,

        Instant createdAt,

        Instant updatedAt
) {
}