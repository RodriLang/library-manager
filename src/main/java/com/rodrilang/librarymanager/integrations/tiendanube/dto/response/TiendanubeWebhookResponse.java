package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

public record TiendanubeWebhookResponse(

        Long id,

        String event,

        String url
) {
}
