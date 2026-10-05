package com.rodrilang.librarymanager.dto.response;

import com.rodrilang.librarymanager.provider.catalog.enums.ProviderBookIdentifierStatus;
import com.rodrilang.librarymanager.provider.catalog.enums.ProviderBookSource;
import com.rodrilang.librarymanager.provider.catalog.enums.ProviderBookVerificationStatus;

import java.time.Instant;

public record BookProviderResponse(

        Long providerId,

        String providerName,

        String providerCode,

        String externalCode,

        String reportedIsbn,

        ProviderBookIdentifierStatus identifierStatus,

        Boolean active,

        Instant firstSeenAt,

        Instant lastSeenAt,

        ProviderBookSource source,

        ProviderBookVerificationStatus verificationStatus

) {
}