package com.rodrilang.librarymanager.importer.price.dto.response;

public record EditorialPriceHealthSummaryResponse(

        long totalBooks,

        long withoutPrice,
        long futureOnly,
        long externalReferenceOnly,

        long stale30Days,
        long stale60Days,
        long stale90Days,
        long unknownConfirmation,

        long missingLatestList,
        long missingMultipleLists,
        long reappeared,

        long decreases,
        long anomalousChanges,
        long extremeChanges,
        long revertedChanges,

        long pendingConflicts,
        long resolvedConflicts,

        long externalSourceDivergences,

        long manualPrices,
        long staleManualPrices,

        long staleProviders
) {
}