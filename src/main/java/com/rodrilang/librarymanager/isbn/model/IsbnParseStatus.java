package com.rodrilang.librarymanager.isbn.model;

public enum IsbnParseStatus {

    VALID,

    RECOVERED_MISSING_CHECK_DIGIT,

    RECOVERED_INVALID_X,

    INVALID
}