package com.rodrilang.librarymanager.metadata.enrichment;

public interface BookMetadataEnrichmentService {

    int enrichPendingBooks(int limit);

    boolean enrichBook(Long bookId);
}