package com.rodrilang.librarymanager.cover.service;

public record BookCoverCandidateSnapshot(
        Long bookId,
        String sourceUrl,
        Integer attempts,
        String currentCoverUrl,
        String currentCoverSource
) {
}