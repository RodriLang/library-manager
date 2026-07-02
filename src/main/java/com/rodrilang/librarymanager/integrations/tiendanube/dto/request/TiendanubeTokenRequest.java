package com.rodrilang.librarymanager.integrations.tiendanube.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TiendanubeTokenRequest(

        @JsonProperty("client_id")
        String clientId,

        @JsonProperty("client_secret")
        String clientSecret,

        @JsonProperty("grant_type")
        String grantType,

        String code
) {
}