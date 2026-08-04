package com.rodrilang.librarymanager.isbn.service;

import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
public class IsbnService {

    public ParsedIsbn parse(String value) {
        String normalized = normalize(value);

        if (normalized == null) {
            return ParsedIsbn.invalid(value, null);
        }

        if (isValidIsbn13(normalized)) {
            return ParsedIsbn.fromIsbn13(value, normalized);
        }

        if (isValidIsbn10(normalized)) {
            return ParsedIsbn.fromIsbn10(
                    value,
                    normalized,
                    convertIsbn10ToIsbn13(normalized)
            );
        }

        if (normalized.matches("97[89]\\d{9}")) {
            String recovered = normalized
                    + calculateIsbn13CheckDigit(normalized);

            return ParsedIsbn.recoveredMissingCheckDigit(
                    value,
                    normalized,
                    recovered
            );
        }

        if (normalized.matches("97[89]\\d{9}X")) {
            String base = normalized.substring(0, 12);
            String recovered = base
                    + calculateIsbn13CheckDigit(base);

            return ParsedIsbn.recoveredInvalidX(
                    value,
                    normalized,
                    recovered
            );
        }

        return ParsedIsbn.invalid(value, normalized);
    }

    public String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^0-9X]", "");

        return normalized.isBlank() ? null : normalized;
    }

    public boolean isValidIsbn10(String value) {
        String isbn = normalize(value);

        if (isbn == null || !isbn.matches("\\d{9}[\\dX]")) {
            return false;
        }

        int sum = 0;

        for (int i = 0; i < isbn.length(); i++) {
            char character = isbn.charAt(i);
            int digit = character == 'X'
                    ? 10
                    : Character.digit(character, 10);

            sum += digit * (10 - i);
        }

        return sum % 11 == 0;
    }

    public boolean isValidIsbn13(String value) {
        String isbn = normalize(value);

        if (isbn == null || !isbn.matches("\\d{13}")) {
            return false;
        }

        String base = isbn.substring(0, 12);
        int expectedCheckDigit = calculateIsbn13CheckDigit(base);
        int actualCheckDigit = Character.digit(isbn.charAt(12), 10);

        return expectedCheckDigit == actualCheckDigit;
    }

    public boolean hasIsbn13Format(String value) {
        String normalized = normalize(value);

        return normalized != null
                && normalized.matches("\\d{13}");
    }

    public boolean hasIsbn10Format(String value) {
        String normalized = normalize(value);

        return normalized != null
                && normalized.matches("\\d{9}[\\dX]");
    }

    public String convertIsbn10ToIsbn13(String value) {
        String isbn10 = normalize(value);

        if (!isValidIsbn10(isbn10)) {
            return null;
        }

        String base = "978" + isbn10.substring(0, 9);

        return base + calculateIsbn13CheckDigit(base);
    }

    public boolean isRecoverableIsbn13(String value) {
        String normalized = normalize(value);

        if (normalized == null) {
            return false;
        }

        /*
         * Caso 1: ISBN-13 sin dígito verificador.
         * Ejemplo: 978987598005
         */
        boolean missingCheckDigit = normalized.matches("97[89]\\d{9}");

        /*
         * Caso 2: ISBN-13 con X incorrecta como último carácter.
         * Ejemplo: 978842541847X
         */
        boolean invalidXCheckDigit = normalized.matches("97[89]\\d{9}X");

        return missingCheckDigit || invalidXCheckDigit;
    }

    public String recoverIsbn13(String value) {
        String normalized = normalize(value);

        if (!isRecoverableIsbn13(normalized)) {
            return null;
        }

        String base = normalized.endsWith("X")
                ? normalized.substring(0, 12)
                : normalized;

        return base + calculateIsbn13CheckDigit(base);
    }

    private int calculateIsbn13CheckDigit(String twelveDigits) {
        if (twelveDigits == null || !twelveDigits.matches("\\d{12}")) {
            throw new IllegalArgumentException(
                    "Se requieren exactamente 12 dígitos."
            );
        }

        int sum = 0;

        for (int i = 0; i < 12; i++) {
            int digit = Character.digit(
                    twelveDigits.charAt(i),
                    10
            );

            sum += digit * (i % 2 == 0 ? 1 : 3);
        }

        return (10 - sum % 10) % 10;
    }
}