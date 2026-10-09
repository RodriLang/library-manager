package com.rodrilang.librarymanager.admin.integration.mercadopago.dto;

import jakarta.validation.constraints.Size;

public record UpdateAdminMercadoPagoPlatformConfigRequest(
        Boolean enabled,
        @Size(max = 255) String clientId,
        String clientSecret,
        String webhookSecret,
        @Size(max = 1000) String oauthRedirectUri,
        @Size(max = 1000) String publicApiBaseUrl,
        @Size(max = 1000) String frontendUrl
) {
}
