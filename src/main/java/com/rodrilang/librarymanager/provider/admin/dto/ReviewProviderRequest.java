package com.rodrilang.librarymanager.provider.admin.dto;

import jakarta.validation.constraints.NotNull;

public record ReviewProviderRequest(
        @NotNull ProviderReviewDecision decision
) {
}
