package com.rodrilang.librarymanager.util;

public final class IsbnUtils {

    private IsbnUtils() {
    }

    public static String normalize(String isbn) {
        if (isbn == null) {
            return null;
        }

        String normalized = isbn
                .replaceAll("[\\s-]", "")
                .trim();

        return normalized.isBlank() ? null : normalized;
    }
}