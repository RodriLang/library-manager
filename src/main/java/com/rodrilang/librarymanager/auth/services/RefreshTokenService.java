package com.rodrilang.librarymanager.auth.services;

import com.rodrilang.librarymanager.auth.dtos.internal.RefreshTokenRotationResult;

public interface RefreshTokenService {

    String generateRefreshToken(String username);

    RefreshTokenRotationResult rotate(String rawToken);

    void revokeToken(String rawToken);

    void cleanupExpiredTokens();
}