package com.rodrilang.librarymanager.editorialprice.dto.response;

import java.util.List;

public record EditorialPriceHealthSummaryResponse(
        long totalBooksWithIssues,
        long nextPeriodSourceConflicts,
        List<EditorialPriceHealthCountResponse> issues
) {
}