package com.rodrilang.librarymanager.auth.dtos.internal;

public record RefreshTokenRotationResult(
        String username,
        String refreshToken
) {
}