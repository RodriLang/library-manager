package com.rodrilang.librarymanager.fiscal.client.dto;

import java.time.Instant;

public record ArcaAccessTicket(

        String token,
        String sign,
        Instant expirationTime

) {
    public boolean isValidAt(Instant instant) {
        return expirationTime != null && expirationTime.isAfter(instant.plusSeconds(300));
    }
}
