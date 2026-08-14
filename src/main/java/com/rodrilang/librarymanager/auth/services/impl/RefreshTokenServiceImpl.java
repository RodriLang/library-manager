package com.rodrilang.librarymanager.auth.services.impl;

import com.rodrilang.librarymanager.auth.dtos.internal.RefreshTokenRotationResult;
import com.rodrilang.librarymanager.auth.exceptions.InvalidTokenException;
import com.rodrilang.librarymanager.auth.models.RefreshToken;
import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.RefreshTokenRepository;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.auth.services.RefreshTokenService;
import com.rodrilang.librarymanager.auth.services.SecureTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@Transactional
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int TOKEN_BYTES = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Duration refreshTokenExpiration;
    private final SecureTokenService secureTokenService;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            @Value("${jwt.refresh-expiration}") Duration refreshTokenExpiration,
            SecureTokenService secureTokenService
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.secureTokenService = secureTokenService;
    }

    @Override
    public String generateRefreshToken(String identifier) {
        User user = userRepository.findByUsernameOrEmail(identifier)
                .orElseThrow(() -> new InvalidTokenException("No se encontró el usuario asociado al refresh token."));

        return createRefreshToken(user);
    }

    @Override
    public RefreshTokenRotationResult rotate(String rawToken) {
        validateRawToken(rawToken);

        String tokenHash = secureTokenService.hash(rawToken);
        Instant now = Instant.now();

        RefreshToken currentToken = refreshTokenRepository
                .findActiveByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("El refresh token es inválido o fue revocado."));

        if (!currentToken.getExpiresAt().isAfter(now)) {

            throw new InvalidTokenException("El refresh token ha vencido.");
        }

        currentToken.setRevokedAt(now);
        currentToken.setRevoked(true);

        User user = currentToken.getUser();
        String newRawToken = createRefreshToken(user);

        log.debug("Refresh token rotated for userId={}", user.getId());

        return new RefreshTokenRotationResult(user.getUsername(), newRawToken);
    }

    @Override
    public void revokeToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        String tokenHash = secureTokenService.hash(rawToken);
        Instant now = Instant.now();

        refreshTokenRepository
                .findByTokenHashAndRevokedFalse(tokenHash)
                .ifPresent(refreshToken -> {
                    refreshToken.setRevoked(true);
                    refreshToken.setRevokedAt(now);
                });

        log.debug("Refresh token revoked");
    }

    @Override
    public void revokeAllForUser(Long userId) {
        int revokedTokens =
                refreshTokenRepository.revokeAllByUserId(userId, Instant.now());

        log.debug(
                "All refresh tokens revoked for userId={}. count={}",
                userId,
                revokedTokens
        );
    }

    @Override
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredTokens() {
        int deletedTokens = refreshTokenRepository.deleteByExpiresAtBefore(Instant.now());

        log.debug("Expired refresh tokens deleted. count={}", deletedTokens);
    }

    private void validateRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException("Debe informar un refresh token.");
        }
    }

    private String createRefreshToken(User user) {
        Instant now = Instant.now();

        String rawToken = secureTokenService.generate(TOKEN_BYTES);
        String tokenHash = secureTokenService.hash(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(now.plus(refreshTokenExpiration))
                .createdAt(now)
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        log.debug("Refresh token generated for userId={}", user.getId());

        return rawToken;
    }
}