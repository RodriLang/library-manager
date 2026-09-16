package com.rodrilang.librarymanager.fiscal.service;

import org.springframework.stereotype.Component;

@Component
public class CuitValidator {

    private static final int[] WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    public boolean isValid(String value) {
        if (value == null || !value.matches("\\d{11}")) return false;

        int sum = 0;
        for (int index = 0; index < WEIGHTS.length; index++) {
            sum += Character.digit(value.charAt(index), 10) * WEIGHTS[index];
        }

        int verifier = 11 - (sum % 11);
        if (verifier == 11) verifier = 0;
        else if (verifier == 10) verifier = 9;

        return verifier == Character.digit(value.charAt(10), 10);
    }
}
