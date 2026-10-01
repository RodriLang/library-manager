package com.rodrilang.librarymanager.purchasing.preference.dto.response;

import com.rodrilang.librarymanager.purchasing.preference.model.ProviderPreferenceSource;

import java.time.Instant;

public record PreferredProviderResponse(
        Long providerId,
        String providerName,
        ProviderPreferenceSource source,
        Instant lastUsedAt,
        boolean currentlyAvailable
) {
    public static PreferredProviderResponse empty() {
        return new PreferredProviderResponse(null, null, null, null, false);
    }
}
