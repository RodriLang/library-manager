package com.rodrilang.librarymanager.shipping.quote.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StorefrontShippingQuoteRequest(
        @NotBlank @Size(max = 20) String postalCode,
        @NotEmpty @Size(max = 100) List<@Valid StorefrontShippingQuoteItemRequest> items
) {}
