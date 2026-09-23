package com.rodrilang.librarymanager.admin.price.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdminPriceAnalyticsResponse(
        int days,
        long increases,
        long decreases,
        long unchanged,
        BigDecimal averagePercentChange,
        List<PriceChange> largestChanges
) {
    public record PriceChange(
            Long bookId,
            String title,
            String isbn,
            BigDecimal previousPrice,
            BigDecimal currentPrice,
            BigDecimal percentChange,
            LocalDate validFrom
    ) {}
}
