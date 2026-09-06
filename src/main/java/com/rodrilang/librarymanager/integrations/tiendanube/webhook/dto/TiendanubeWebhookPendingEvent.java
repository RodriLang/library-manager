package com.rodrilang.librarymanager.integrations.tiendanube.webhook.dto;

public record TiendanubeWebhookPendingEvent(
        Long id,
        Long tiendanubeStoreId,
        Long storeId,
        String event,
        Long resourceId,
        String payload,
        int attemptCount,
        int maxAttempts
) {
}
