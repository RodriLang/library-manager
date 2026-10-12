package com.rodrilang.librarymanager.shipping.returns.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReturnRestockItemRequest(
        @NotNull Long inventoryId,
        @NotNull @Min(1) Integer quantity
) {}
