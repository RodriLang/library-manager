package com.rodrilang.librarymanager.inventory.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InventoryPriceOverviewResponse(
        Long inventoryId,
        Long bookId,
        String isbn,
        String title,
        List<String> authors,
        String publisher,
        BigDecimal currentPrice,
        LocalDate currentPriceEffectiveFrom,
        BigDecimal previousPrice,
        BigDecimal currentChangePercent,
        BigDecimal nextPrice,
        LocalDate nextPriceEffectiveFrom,
        LocalDate lastPriceCheckedAt,
        boolean missingPrice,
        boolean stale,
        boolean increased,
        boolean decreased,
        boolean scheduled,
        boolean reviewRequired
) {
}
