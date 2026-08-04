package com.rodrilang.librarymanager.importer.price.configuration.dto;

public record ProviderBookRegistrationResult(
        Long providerBookId,
        Long providerId,
        Long bookId,
        String externalCode,
        boolean created
) {
}