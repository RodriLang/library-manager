package com.rodrilang.librarymanager.purchasing.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreatePurchaseRequest(
        @NotNull Long providerId,
        @NotNull LocalDate purchaseDate,
        @Size(max = 80) String documentNumber,
        String notes
) {
}
