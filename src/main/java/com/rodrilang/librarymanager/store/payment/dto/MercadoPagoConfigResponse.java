package com.rodrilang.librarymanager.store.payment.dto;

import java.time.Instant;

public record MercadoPagoConfigResponse(
        boolean applicationConfigured,
        boolean connected,
        boolean enabled,
        boolean requiresReconnect,
        Long mercadoPagoUserId,
        Instant connectedAt,
        Instant tokenExpiresAt,
        String connectionError,
        String webhookUrl
) {}
