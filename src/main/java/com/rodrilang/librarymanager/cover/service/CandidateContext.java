package com.rodrilang.librarymanager.cover.service;

public record CandidateContext(
        Long bookId,
        String sourceUrl,
        Integer attempts,
        CoverCandidateDecision decision
) {
}