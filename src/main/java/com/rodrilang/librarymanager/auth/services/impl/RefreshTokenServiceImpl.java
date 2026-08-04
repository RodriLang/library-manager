package com.rodrilang.librarymanager.auth.services.impl;

import com.rodrilang.librarymanager.auth.dtos.internal.RefreshTokenRotationResult;
import com.rodrilang.librarymanager.auth.exceptions.InvalidTokenException;
import com.rodrilang.librarymanager.auth.models.RefreshToken;
import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.RefreshTokenRepository;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.auth.services.RefreshTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@Transactional
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int TOKEN_BYTES = 64;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Duration refreshTokenExpiration;
    private final SecureRandom secureRandom;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            @Value("${jwt.refresh-expiration}") Duration refreshTokenExpiration
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public String generateRefreshToken(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidTokenException("No se encontró el usuario asociado al refresh token."));

        return createRefreshToken(user);
    }

    @Override
    public RefreshTokenRotationResult rotate(String rawToken) {
        validateRawToken(rawToken);

        String tokenHash = hashToken(rawToken);
        Instant now = Instant.now();

        RefreshToken currentToken = refreshTokenRepository
                .findActiveByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("El refresh token es inválido o fue revocado."));

        if (!currentToken.getExpiresAt().isAfter(now)) {
            currentToken.setUsedAt(now);
            currentToken.setRevoked(true);

            throw new InvalidTokenException("El refresh token ha vencido.");
        }

        currentToken.setUsedAt(now);
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

        String tokenHash = hashToken(rawToken);

        refreshTokenRepository
                .findByTokenHashAndRevokedFalse(tokenHash)
                .ifPresent(refreshToken -> refreshToken.setRevoked(true));

        log.debug("Refresh token revoked");
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

    private String generateSecureToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);

            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No se pudo generar el hash del refresh token.", exception);
        }
    }

    private String createRefreshToken(User user) {
        Instant now = Instant.now();

        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);

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