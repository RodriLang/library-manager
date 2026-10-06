package com.rodrilang.librarymanager.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InventoryQuantityRequest(
        @NotNull @Min(1) Integer quantity,
        @Min(0) Integer consignmentQuantity,
        @Positive Long consignmentProviderId
) {
    public InventoryQuantityRequest(Integer quantity) {
        this(quantity, 0, null);
    }
}
