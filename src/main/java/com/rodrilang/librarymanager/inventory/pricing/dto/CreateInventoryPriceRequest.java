package com.rodrilang.librarymanager.inventory.pricing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateInventoryPriceRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate effectiveFrom
) {
}
