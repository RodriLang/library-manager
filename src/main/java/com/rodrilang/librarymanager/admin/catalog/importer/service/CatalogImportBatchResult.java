package com.rodrilang.librarymanager.admin.catalog.importer.service;

public record CatalogImportBatchResult(int processedRows, int createdBooks, int enrichedBooks, int unchangedBooks,
                                       int conflictedBooks, int skippedRows, int errorCount,
                                       int providerLinksCreated, int providerLinksUpdated) {
    public static CatalogImportBatchResult empty() { return new CatalogImportBatchResult(0,0,0,0,0,0,0,0,0); }
    public CatalogImportBatchResult plus(CatalogImportBatchResult o) {
        return new CatalogImportBatchResult(processedRows+o.processedRows, createdBooks+o.createdBooks,
                enrichedBooks+o.enrichedBooks, unchangedBooks+o.unchangedBooks, conflictedBooks+o.conflictedBooks,
                skippedRows+o.skippedRows, errorCount+o.errorCount,
                providerLinksCreated+o.providerLinksCreated, providerLinksUpdated+o.providerLinksUpdated);
    }
}
