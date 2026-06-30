package com.rodrilang.librarymanager.dto.response;

import java.util.Set;

public record BookSummaryResponse(

        Long id,

        String isbn,

        String title,

        String thumbnailUrl,

        String publisherName,

        Set<AuthorResponse> authors
) {
}