package com.rodrilang.librarymanager.inventory.pricing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ConfirmInventoryPriceRequest(
        @NotNull LocalDate confirmedAt,
        @Size(max = 150) String source
) {
}
