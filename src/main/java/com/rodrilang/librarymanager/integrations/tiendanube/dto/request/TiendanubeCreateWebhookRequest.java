package com.rodrilang.librarymanager.integrations.tiendanube.dto.request;

public record TiendanubeCreateWebhookRequest(

        String event,

        String url
) {
}