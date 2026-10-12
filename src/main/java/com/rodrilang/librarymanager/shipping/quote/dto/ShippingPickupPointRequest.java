package com.rodrilang.librarymanager.shipping.quote.dto;

import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ShippingPickupPointRequest(
        @NotNull ShippingProviderType provider,
        @NotBlank @Size(max = 160) String externalId
) {}
