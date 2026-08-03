package com.rodrilang.librarymanager.importer.price.dto;

import com.rodrilang.librarymanager.importer.price.configuration.enums.ProviderBookIdentifierStatus;
import com.rodrilang.librarymanager.importer.price.enums.PriceListIdentifierType;

public record PriceListIdentifier(

        PriceListIdentifierType type,

        String isbn10,

        String isbn13,

        String externalCode,

        String reportedIsbn,

        ProviderBookIdentifierStatus status
) {

    public static PriceListIdentifier isbn(
            String isbn10,
            String isbn13,
            String externalCode,
            String reportedIsbn,
            ProviderBookIdentifierStatus status
    ) {
        return new PriceListIdentifier(
                PriceListIdentifierType.ISBN,
                isbn10,
                isbn13,
                externalCode,
                reportedIsbn,
                status
        );
    }

    public static PriceListIdentifier externalCode(
            String externalCode,
            String reportedIsbn,
            ProviderBookIdentifierStatus status
    ) {
        return new PriceListIdentifier(
                PriceListIdentifierType.EXTERNAL_CODE,
                null,
                null,
                externalCode,
                reportedIsbn,
                status
        );
    }

    public static PriceListIdentifier empty() {
        return new PriceListIdentifier(
                PriceListIdentifierType.EMPTY,
                null,
                null,
                null,
                null,
                ProviderBookIdentifierStatus.NO_IDENTIFIER
        );
    }
}