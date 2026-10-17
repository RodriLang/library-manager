package com.rodrilang.librarymanager.inventory.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProviderReportedPriceResponse(
        Long providerId,
        String providerName,
        BigDecimal price,
        LocalDate effectiveFrom,
        boolean latestPeriod
) {
}
