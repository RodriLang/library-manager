package com.rodrilang.librarymanager.fiscal.client.dto;

import java.time.LocalDate;
import java.util.Locale;

public record ArcaPointOfSale(
        int number,
        String emissionType,
        boolean blocked,
        LocalDate deactivationDate
) {

    public boolean active() {
        return !blocked && deactivationDate == null;
    }

    public boolean usesCae() {
        return "CAE".equals(emissionMode());
    }

    public boolean usesCaea() {
        return "CAEA".equals(emissionMode());
    }

    private String emissionMode() {
        if (emissionType == null || emissionType.isBlank()) {
            return "";
        }

        String normalized = emissionType.trim().toUpperCase(Locale.ROOT);
        int separator = normalized.indexOf(" - ");

        return separator >= 0
                ? normalized.substring(0, separator).trim()
                : normalized;
    }
}