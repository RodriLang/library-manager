package com.rodrilang.librarymanager.importer.price.configuration.enums;

public enum ProviderBookIdentifierStatus {

    VALID_ISBN,

    RECOVERED_MISSING_CHECK_DIGIT,

    RECOVERED_INVALID_X,

    RECOVERED_FROM_ISBN10,

    RECOVERED_FROM_CODE_COLUMN,

    EXTERNAL_CODE,

    INVALID_UNRESOLVED,

    NO_IDENTIFIER,

    MANUALLY_CONFIRMED
}