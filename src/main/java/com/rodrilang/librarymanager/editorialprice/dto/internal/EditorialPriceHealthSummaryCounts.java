package com.rodrilang.librarymanager.editorialprice.dto.internal;

public record EditorialPriceHealthSummaryCounts(
        long totalBooksWithIssues,
        long sourceConflict,
        long nextPeriodSourceConflict,
        long noCurrentPrice,
        long futureOnly,
        long staleEvidence,
        long externalReferenceOnly
) {
}