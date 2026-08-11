package com.rodrilang.librarymanager.dto.response;

import com.rodrilang.librarymanager.importer.price.configuration.enums.ProviderBookIdentifierStatus;

import java.time.Instant;

public record BookProviderResponse(

        Long providerId,

        String providerName,

        String providerCode,

        String externalCode,

        String reportedIsbn,

        ProviderBookIdentifierStatus identifierStatus,

        Boolean active,

        Instant lastSeenAt

) {
}