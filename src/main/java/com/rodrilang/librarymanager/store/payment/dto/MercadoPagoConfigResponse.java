package com.rodrilang.librarymanager.store.payment.dto;

public record MercadoPagoConfigResponse(
        boolean enabled,
        boolean configured,
        String accessTokenMasked,
        boolean webhookSecretConfigured,
        String webhookUrl
) {}
