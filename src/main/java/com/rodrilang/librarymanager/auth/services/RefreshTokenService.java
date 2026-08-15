package com.rodrilang.librarymanager.auth.services;

import com.rodrilang.librarymanager.auth.dtos.internal.RefreshTokenRotationResult;

public interface RefreshTokenService {

    String generateRefreshToken(String identifier);

    RefreshTokenRotationResult rotate(String rawToken);

    void revokeToken(String rawToken);

    void revokeAllForUser(Long userId);

    void revokeAllForBookstore(Long bookstoreId);

    void cleanupExpiredTokens();
}