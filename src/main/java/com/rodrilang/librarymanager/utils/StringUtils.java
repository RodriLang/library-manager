package com.rodrilang.librarymanager.utils;

public class StringUtils {

    private StringUtils() {
    }

    public static String normalizeName(String value) {
        return value
                .trim()
                .replaceAll("\\s+", " ");
    }
}
