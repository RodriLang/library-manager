package com.rodrilang.librarymanager.shipping.quote.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StorefrontShippingQuoteItemRequest(
        @NotNull Long inventoryId,
        @NotNull @Min(1) Integer quantity
) {}
