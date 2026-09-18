package com.rodrilang.librarymanager.fiscal.client.dto;

import java.time.LocalDate;

public record ArcaAuthorizationResult(

        boolean authorized,
        String result,
        String cae,
        LocalDate caeExpirationDate,
        String observations,
        String errors

) {
}
