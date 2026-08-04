package com.rodrilang.librarymanager.isbn.model;

public record ParsedIsbn(

        String rawValue,

        String normalizedValue,

        String isbn10,

        String isbn13,

        IsbnParseStatus status
) {

    public static ParsedIsbn invalid(
            String rawValue,
            String normalizedValue
    ) {
        return new ParsedIsbn(
                rawValue,
                normalizedValue,
                null,
                null,
                IsbnParseStatus.INVALID
        );
    }

    public static ParsedIsbn fromIsbn13(
            String rawValue,
            String isbn13
    ) {
        return new ParsedIsbn(
                rawValue,
                isbn13,
                null,
                isbn13,
                IsbnParseStatus.VALID
        );
    }

    public static ParsedIsbn fromIsbn10(
            String rawValue,
            String isbn10,
            String isbn13
    ) {
        return new ParsedIsbn(
                rawValue,
                isbn10,
                isbn10,
                isbn13,
                IsbnParseStatus.VALID
        );
    }

    public static ParsedIsbn recoveredMissingCheckDigit(
            String rawValue,
            String normalizedValue,
            String isbn13
    ) {
        return new ParsedIsbn(
                rawValue,
                normalizedValue,
                null,
                isbn13,
                IsbnParseStatus.RECOVERED_MISSING_CHECK_DIGIT
        );
    }

    public static ParsedIsbn recoveredInvalidX(
            String rawValue,
            String normalizedValue,
            String isbn13
    ) {
        return new ParsedIsbn(
                rawValue,
                normalizedValue,
                null,
                isbn13,
                IsbnParseStatus.RECOVERED_INVALID_X
        );
    }

    public boolean valid() {
        return status != IsbnParseStatus.INVALID;
    }

    public boolean recovered() {
        return status == IsbnParseStatus.RECOVERED_MISSING_CHECK_DIGIT
                || status == IsbnParseStatus.RECOVERED_INVALID_X;
    }

    public String preferredIsbn() {
        return isbn13 != null ? isbn13 : isbn10;
    }
}