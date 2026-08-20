package com.rodrilang.librarymanager.auth.services.impl;

import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.auth.services.RefreshTokenService;
import com.rodrilang.librarymanager.auth.services.UserPasswordService;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPasswordServiceImpl implements UserPasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional
    public void changePassword(
            Long userId,
            String currentPassword,
            String newPassword
    ) {
        User user = userRepository.findByIdAndEnabledTrueAndAccountLockedFalse(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario autenticado."));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadCredentialsException("La contraseña actual es incorrecta.");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("La nueva contraseña debe ser diferente de la actual.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiration(null);

        refreshTokenService.revokeAllForUser(user.getId());
    }
}