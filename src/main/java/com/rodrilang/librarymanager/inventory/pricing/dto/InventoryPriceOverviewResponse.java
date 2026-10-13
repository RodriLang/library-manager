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
        Long currentPriceImportId,
        Long currentPriceImportProviderId,
        String currentPriceImportProviderName,
        String currentPriceImportSourceName,
        String currentPriceImportFilename,
        Long currentPriceLastConfirmedImportId,
        Long currentPriceLastConfirmedProviderId,
        String currentPriceLastConfirmedProviderName,
        String currentPriceLastConfirmedImportSourceName,
        String currentPriceLastConfirmedImportFilename,
        LocalDate currentPriceLastConfirmedAt,
        String currentPriceLastConfirmedSource,
        Long daysSinceConfirmation,
        boolean confirmedThisMonth,
        BigDecimal previousPrice,
        LocalDate previousPriceEffectiveFrom,
        BigDecimal currentChangePercent,
        BigDecimal nextPrice,
        LocalDate nextPriceEffectiveFrom,
        BigDecimal nextChangePercent,
        boolean missingPrice,
        boolean stale,
        boolean increased,
        boolean decreased,
        boolean scheduled,
        boolean reviewRequired
) {
}
