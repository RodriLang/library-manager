package com.rodrilang.librarymanager.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdjustConsignmentRequest(
        @NotNull @Min(0) Integer consignmentStock,
        @Positive Long providerId,
        String note
) {}
