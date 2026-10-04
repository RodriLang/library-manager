package com.rodrilang.librarymanager.catalog.candidate.dto.internal;

import java.time.Instant;

public record CatalogCandidateLookupData(
        Long candidateId,
        String isbn13,
        Instant attemptedAt
) {
}