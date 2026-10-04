package com.rodrilang.librarymanager.catalog.candidate.dto.internal;

import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidate;

public record CatalogCandidateBookResolutionResult(
        CatalogCandidate candidate,
        boolean resolutionReused
) {
}