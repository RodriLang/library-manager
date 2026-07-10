package com.rodrilang.librarymanager.metadata.cover.job.dto;

import com.rodrilang.librarymanager.metadata.cover.job.model.CoverEnrichmentJobStatus;

public record CoverEnrichmentJobStatusResponse(
        Long jobId,
        CoverEnrichmentJobStatus status,
        int totalBooks,
        int processedBooks,
        int foundCovers,
        int notFoundCovers,
        int errorCount,
        int progressPercentage,
        String errorMessage
) {
}