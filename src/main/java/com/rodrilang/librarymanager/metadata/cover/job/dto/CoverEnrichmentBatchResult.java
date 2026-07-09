package com.rodrilang.librarymanager.metadata.cover.job.dto;

public record CoverEnrichmentBatchResult(
        int processedBooks,
        int foundCovers,
        int notFoundCovers,
        int errorCount
) {
}