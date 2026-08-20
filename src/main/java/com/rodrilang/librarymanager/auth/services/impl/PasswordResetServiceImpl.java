package com.rodrilang.librarymanager.auth.services.impl;

import com.rodrilang.librarymanager.auth.events.PasswordResetEmailEvent;
import com.rodrilang.librarymanager.auth.exceptions.ExpiredPasswordResetTokenException;
import com.rodrilang.librarymanager.auth.exceptions.InvalidPasswordResetTokenException;
import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.auth.services.PasswordResetService;
import com.rodrilang.librarymanager.auth.services.RefreshTokenService;
import com.rodrilang.librarymanager.auth.services.SecureTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Duration TOKEN_EXPIRATION = Duration.ofMinutes(30);
    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final RefreshTokenService refreshTokenService;
    private final SecureTokenService secureTokenService;

    @Override
    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = normalizeEmail(email);

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

        if (userOpt.isEmpty()) {
            return;
        }

        User user = userOpt.get();

        if (!user.isEnabled() || user.isAccountLocked()) {
            return;
        }

        String rawToken = secureTokenService.generate(TOKEN_BYTES);
        String tokenHash = secureTokenService.hash(rawToken);

        user.setPasswordResetToken(tokenHash);
        user.setPasswordResetTokenExpiration(Instant.now().plus(TOKEN_EXPIRATION));

        eventPublisher.publishEvent(
                new PasswordResetEmailEvent(
                        user.getEmail(),
                        rawToken
                )
        );
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidPasswordResetTokenException("El enlace de recuperación no es válido.");
        }

        String tokenHash = secureTokenService.hash(rawToken);

        User user = userRepository
                .findByPasswordResetTokenAndEnabledTrueAndAccountLockedFalse(tokenHash)
                .orElseThrow(() -> new InvalidPasswordResetTokenException("El enlace de recuperación no es válido."));

        Instant expiration = user.getPasswordResetTokenExpiration();

        if (expiration == null || !expiration.isAfter(Instant.now())) {

            throw new ExpiredPasswordResetTokenException("El enlace de recuperación ha expirado.");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("La nueva contraseña debe ser diferente de la actual.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));

        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiration(null);

        refreshTokenService.revokeAllForUser(user.getId());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }


}