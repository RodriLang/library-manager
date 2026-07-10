package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TiendanubeTokenResponse(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        String scope,

        @JsonProperty("user_id")
        Long userId
) {
}