package com.rodrilang.librarymanager.admin.integration.mercadopago.dto;

public record AdminMercadoPagoPlatformConfigResponse(
        boolean enabled,
        boolean configured,
        boolean encryptionConfigured,
        String source,
        String clientId,
        boolean clientSecretConfigured,
        String clientSecretMasked,
        boolean webhookSecretConfigured,
        String webhookSecretMasked,
        String oauthRedirectUri,
        String webhookUrl,
        String publicApiBaseUrl,
        String frontendUrl
) {
}
