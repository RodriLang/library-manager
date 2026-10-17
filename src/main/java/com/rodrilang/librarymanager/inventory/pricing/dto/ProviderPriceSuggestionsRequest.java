package com.rodrilang.librarymanager.inventory.pricing.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProviderPriceSuggestionsRequest(
        @NotEmpty
        @Size(max = 200)
        List<@NotNull Long> bookIds
) {
}
