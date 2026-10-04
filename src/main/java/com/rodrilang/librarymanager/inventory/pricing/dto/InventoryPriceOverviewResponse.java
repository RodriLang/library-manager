package com.rodrilang.librarymanager.inventory.pricing.dto;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceSource;

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
        InventoryPriceSource currentPriceSource,
        LocalDate currentPriceLastConfirmedAt,
        String currentPriceLastConfirmedSource,
        Long daysSinceConfirmation,
        boolean confirmedThisMonth,
        BigDecimal previousPrice,
        LocalDate previousPriceEffectiveFrom,
        BigDecimal currentChangePercent,
        BigDecimal nextPrice,
        LocalDate nextPriceEffectiveFrom,
        boolean missingPrice,
        boolean stale,
        boolean increased,
        boolean decreased,
        boolean scheduled,
        boolean reviewRequired
) {
}
