package com.rodrilang.librarymanager.auth.dtos.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserResponse user
) {
}

