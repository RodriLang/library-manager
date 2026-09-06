package com.rodrilang.librarymanager.integrations.tiendanube.webhook.dto;

import java.util.UUID;

public record TiendanubeClaimedWebhookEvent(
        Long id,
        Long tiendanubeStoreId,
        Long storeId,
        String event,
        Long resourceId,
        String payload,
        int attemptCount,
        int maxAttempts,
        UUID processingToken
) {
}
