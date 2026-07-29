package com.rodrilang.librarymanager.util;

import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalizeForSort(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return removeAccents(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("^[^a-z0-9]+", "")
                .trim();
    }

    public static String normalizeForMatch(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return removeAccents(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String removeAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}