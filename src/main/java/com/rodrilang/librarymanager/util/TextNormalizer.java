package com.rodrilang.librarymanager.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class TextNormalizer {

    private static final Set<String> SEARCH_STOP_WORDS = Set.of(
            "el",
            "la",
            "los",
            "las",
            "un",
            "una",
            "unos",
            "unas",
            "de",
            "del",
            "al",
            "y",
            "e"
    );

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

    public static String normalizeForSearch(String value) {
        String normalized = normalizeForSort(value)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isEmpty()) {
            return "";
        }

        String searchValue = Arrays.stream(normalized.split("\\s+"))
                .filter(token -> !SEARCH_STOP_WORDS.contains(token))
                .collect(Collectors.joining(" "));

        return searchValue.isBlank() ? normalized : searchValue;
    }

    public static String normalizeForFullTextSearch(String value) {
        String normalized = normalizeForSearch(value);

        if (normalized.isBlank()) {
            return "";
        }

        String[] tokens = normalized.split("\\s+");

        if (tokens.length == 1) {
            return tokens[0] + ":*";
        }

        StringBuilder query = new StringBuilder();

        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                query.append(" & ");
            }

            query.append(tokens[i]);

            if (i == tokens.length - 1) {
                query.append(":*");
            }
        }

        return query.toString();
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