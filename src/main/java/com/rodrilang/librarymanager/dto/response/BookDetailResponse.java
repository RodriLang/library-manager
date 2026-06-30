package com.rodrilang.librarymanager.dto.response;

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

        Boolean active,

        PublisherResponse publisher,

        Set<AuthorResponse> authors,

        Instant createdAt,

        Instant updatedAt
) {
}