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
            return ParsedIsbn.fromIsbn10(value, normalized, calculateIsbn13FromIsbn10(normalized));
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
            int digit = character == 'X' ? 10 : Character.digit(character, 10);
            sum += digit * (10 - i);
        }

        return sum % 11 == 0;
    }

    public boolean isValidIsbn13(String value) {
        String isbn = normalize(value);

        if (isbn == null || !isbn.matches("\\d{13}")) {
            return false;
        }

        int sum = 0;

        for (int i = 0; i < 12; i++) {
            int digit = Character.digit(isbn.charAt(i), 10);
            sum += digit * (i % 2 == 0 ? 1 : 3);
        }

        int expectedCheckDigit = (10 - sum % 10) % 10;
        int actualCheckDigit = Character.digit(isbn.charAt(12), 10);

        return expectedCheckDigit == actualCheckDigit;
    }

    public boolean hasIsbn13Format(String value) {
        String normalized = normalize(value);
        return normalized != null && normalized.matches("\\d{13}");
    }

    public boolean hasIsbn10Format(String value) {
        String normalized = normalize(value);
        return normalized != null && normalized.matches("\\d{9}[\\dX]");
    }

    private String calculateIsbn13FromIsbn10(String isbn10) {
        String base = "978" + isbn10.substring(0, 9);
        int sum = 0;

        for (int i = 0; i < base.length(); i++) {
            int digit = Character.digit(base.charAt(i), 10);
            sum += digit * (i % 2 == 0 ? 1 : 3);
        }

        return base + ((10 - sum % 10) % 10);
    }
}