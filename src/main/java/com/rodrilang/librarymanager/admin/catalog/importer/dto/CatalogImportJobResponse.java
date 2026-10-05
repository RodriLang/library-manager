package com.rodrilang.librarymanager.admin.catalog.importer.dto;

import com.rodrilang.librarymanager.admin.catalog.importer.model.CatalogImportPhase;
import com.rodrilang.librarymanager.admin.catalog.importer.model.CatalogImportStatus;
import java.time.Instant;
import java.util.List;

public record CatalogImportJobResponse(
        Long id, Long providerId, String providerName, Long formatId, String formatName,
        String originalFilename, CatalogImportStatus status, CatalogImportPhase phase,
        int totalRows, int processedRows, int createdBooks, int enrichedBooks, int unchangedBooks,
        int conflictedBooks, int skippedRows, int errorCount,
        int providerLinksCreated, int providerLinksUpdated,
        Instant startedAt, Instant finishedAt, String failureMessage,
        List<ErrorResponse> errors
) {
    public record ErrorResponse(Integer rowNumber, String isbn, String title, String errorType, String message) {}
}
