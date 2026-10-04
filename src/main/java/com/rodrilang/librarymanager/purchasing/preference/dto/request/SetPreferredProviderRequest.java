package com.rodrilang.librarymanager.purchasing.preference.dto.request;

import jakarta.validation.constraints.NotNull;

public record SetPreferredProviderRequest(
        @NotNull Long providerId
) {
}
