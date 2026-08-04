package com.rodrilang.librarymanager.auth.dtos.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(

        @NotBlank(message = "Refresh accessToken is required")
        String refreshToken

) {
}


