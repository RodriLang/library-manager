package com.rodrilang.librarymanager.inventory.consignment.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateConsignmentSettlementRequest(
        @NotNull @Positive Long providerId,
        @NotEmpty List<@Positive Long> movementIds,
        @Size(max = 1000) String notes
) {}
