package com.rodrilang.librarymanager.catalog.candidate.dto.response;

import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidateStatus;

import java.time.Instant;

public record CatalogCandidateResponse(

        Long id,

        String isbn10,

        String isbn13,

        CatalogCandidateStatus status,

        Long resolvedBookId,

        String resolvedBookTitle,

        Long firstSeenByBookstoreId,

        Instant resolvedAt,

        Instant createdAt,

        Instant updatedAt

) {
}
