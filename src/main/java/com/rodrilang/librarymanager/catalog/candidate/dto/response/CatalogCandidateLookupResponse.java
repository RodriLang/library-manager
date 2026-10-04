package com.rodrilang.librarymanager.catalog.candidate.dto.response;

import java.time.Instant;

public record CatalogCandidateLookupResponse(
        boolean found,
        Instant attemptedAt,
        Long bookId,
        String bookTitle
) {
}