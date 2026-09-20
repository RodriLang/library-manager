package com.rodrilang.librarymanager.fiscal.client.dto;

import java.time.LocalDate;

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
        return "CAE".equalsIgnoreCase(emissionType);
    }
}