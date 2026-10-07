package com.rodrilang.librarymanager.dto.response;

import com.rodrilang.librarymanager.enums.BookCatalogStatus;
import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookFieldSource;
import com.rodrilang.librarymanager.catalog.contribution.dto.BookFieldOverrideResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record BookDetailResponse(

        Long id,

        String isbn,

        String title,

        String subtitle,

        String description,

        String language,

        Integer pageCount,

        Integer publicationYear,

        Integer publicationMonth,

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

        Map<String, BookFieldSource> fieldSources,

        Map<String, BookFieldOverrideResponse> fieldOverrides,

        List<BookProviderResponse> providers,

        Instant createdAt,

        Instant updatedAt
) {
}