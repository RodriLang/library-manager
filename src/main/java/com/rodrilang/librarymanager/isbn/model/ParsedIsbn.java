package com.rodrilang.librarymanager.isbn.model;

public record ParsedIsbn(
        String rawValue,
        String normalizedValue,
        String isbn10,
        String isbn13,
        boolean valid
) {

    public static ParsedIsbn invalid(String rawValue, String normalizedValue) {
        return new ParsedIsbn(rawValue, normalizedValue, null, null, false);
    }

    public static ParsedIsbn fromIsbn13(String rawValue, String isbn13) {
        return new ParsedIsbn(rawValue, isbn13, null, isbn13, true);
    }

    public static ParsedIsbn fromIsbn10(String rawValue, String isbn10, String isbn13) {
        return new ParsedIsbn(rawValue, isbn10, isbn10, isbn13, true);
    }

    public String preferredIsbn() {
        return isbn13 != null ? isbn13 : isbn10;
    }
}