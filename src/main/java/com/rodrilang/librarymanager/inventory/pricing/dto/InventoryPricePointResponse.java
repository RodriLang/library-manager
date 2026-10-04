package com.rodrilang.librarymanager.inventory.pricing.dto;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InventoryPricePointResponse(
        Long id,
        BigDecimal amount,
        LocalDate effectiveFrom,
        InventoryPriceSource source,
        Long priceImportId,
        LocalDate lastConfirmedAt,
        String lastConfirmedSource,
        Instant createdAt
) {
}
