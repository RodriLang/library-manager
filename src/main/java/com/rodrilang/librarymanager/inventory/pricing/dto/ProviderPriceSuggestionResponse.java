package com.rodrilang.librarymanager.inventory.pricing.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record ProviderPriceSuggestionResponse(
        Long bookId,
        BigDecimal suggestedPrice,
        boolean autoApplicable,
        ProviderPriceSuggestionStatus status,
        ProviderPriceFreshness freshness,
        YearMonth latestPeriod,
        List<ProviderReportedPriceResponse> prices
) {
}
