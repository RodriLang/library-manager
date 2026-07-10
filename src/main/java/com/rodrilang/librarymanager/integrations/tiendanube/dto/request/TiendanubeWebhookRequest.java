package com.rodrilang.librarymanager.integrations.tiendanube.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TiendanubeWebhookRequest(

        @JsonProperty("store_id")
        Long storeId,

        String event,

        Long id
) {
}