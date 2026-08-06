package com.rodrilang.librarymanager.importer.price.dto.internal;

import com.rodrilang.librarymanager.importer.price.configuration.enums.ProviderBookIdentifierStatus;

public record ProviderBookUpsertRow(
        Long bookId,
        String externalCode,
        String reportedIsbn,
        ProviderBookIdentifierStatus identifierStatus
) {
}