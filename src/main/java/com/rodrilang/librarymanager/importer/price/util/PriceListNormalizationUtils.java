package com.rodrilang.librarymanager.importer.price.util;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class PriceListNormalizationUtils {

    private PriceListNormalizationUtils() {
    }

    public static String normalizeName(String value) {
        return java.text.Normalizer.normalize(value.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    public static String formatName(String value) {
        return Arrays.stream(value.trim().toLowerCase().split("\\s+"))
                .map(word -> word.isBlank()
                        ? word
                        : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    public static String formatNullable(String value) {
        if (!hasText(value)) {
            return null;
        }

        return formatName(value);
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}