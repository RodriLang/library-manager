package com.rodrilang.librarymanager.dto.response;

import com.rodrilang.librarymanager.enums.BookCatalogStatus;
import com.rodrilang.librarymanager.enums.BookSource;

import java.math.BigDecimal;
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

        String coverSource,

        String categoryName,

        String genreName,

        BigDecimal weightGrams,

        BigDecimal widthCm,

        BigDecimal heightCm,

        BigDecimal depthCm,

        BookSource source,

        BookCatalogStatus catalogStatus,

        Boolean active,

        PublisherResponse publisher,

        Set<AuthorResponse> authors,

        EditorialPriceResponse editorialPrice,

        Instant createdAt,

        Instant updatedAt
) {
}