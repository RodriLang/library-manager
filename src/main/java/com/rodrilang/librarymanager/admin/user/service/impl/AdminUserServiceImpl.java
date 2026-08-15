package com.rodrilang.librarymanager.admin.user.service.impl;

import com.rodrilang.librarymanager.admin.user.dto.AdminUpdateUserRequest;
import com.rodrilang.librarymanager.admin.user.dto.AdminUserResponse;
import com.rodrilang.librarymanager.admin.user.mapper.AdminUserMapper;
import com.rodrilang.librarymanager.admin.user.service.AdminUserService;
import com.rodrilang.librarymanager.admin.user.specification.UserSpecifications;
import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.auth.services.PasswordResetService;
import com.rodrilang.librarymanager.auth.services.RefreshTokenService;
import com.rodrilang.librarymanager.exception.DuplicateResourceException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private static final String USER_NOT_FOUND = "No se encontró el usuario.";

    private final UserRepository userRepository;
    private final AdminUserMapper adminUserMapper;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> findAll(
            String search,
            Boolean enabled,
            Boolean locked,
            Pageable pageable
    ) {
        Specification<User> specification =
                UserSpecifications.matchesSearch(search)
                        .and(UserSpecifications.hasEnabled(enabled))
                        .and(UserSpecifications.hasLocked(locked));

        return userRepository
                .findAll(specification, pageable)
                .map(adminUserMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserResponse findById(Long userId) {
        return adminUserMapper.toDto(getUser(userId));
    }

    @Override
    @Transactional
    public AdminUserResponse updateUser(Long userId, AdminUpdateUserRequest request) {
        User user = getUser(userId);

        String username = normalize(request.username());
        String email = normalize(request.email());

        boolean usernameChanged = !user.getUsername().equals(username);

        boolean emailChanged = !user.getEmail().equals(email);

        if (usernameChanged && userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Ya existe un usuario registrado como " + username);
        }

        if (emailChanged && userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Ya existe un usuario registrado con el correo " + email);
        }

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setUsername(username);
        user.setEmail(email);

        if (emailChanged) {
            user.setPasswordResetToken(null);
            user.setPasswordResetTokenExpiration(null);
        }

        if (usernameChanged || emailChanged) {
            refreshTokenService.revokeAllForUser(user.getId());
        }

        return adminUserMapper.toDto(user);
    }

    @Override
    @Transactional
    public void lock(Long userId) {
        User user = getUser(userId);

        if (user.isAccountLocked()) {
            return;
        }

        user.setAccountLocked(true);

        refreshTokenService.revokeAllForUser(user.getId());
    }

    @Override
    @Transactional
    public void unlock(Long userId) {
        User user = getUser(userId);

        user.setAccountLocked(false);
    }

    @Override
    @Transactional
    public void disable(Long userId) {
        User user = getUser(userId);

        if (!user.isEnabled()) {
            return;
        }

        user.setEnabled(false);

        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiration(null);

        refreshTokenService.revokeAllForUser(user.getId());
    }

    @Override
    @Transactional
    public void enable(Long userId) {
        User user = getUser(userId);

        user.setEnabled(true);
    }

    @Override
    @Transactional
    public void revokeSessions(Long userId) {
        User user = getUser(userId);

        refreshTokenService.revokeAllForUser(user.getId());
    }

    @Override
    @Transactional
    public void sendPasswordReset(Long userId) {
        User user = getUser(userId);

        passwordResetService.requestReset(user.getEmail());
    }

    private User getUser(Long userId) {
        return userRepository
                .findByIdForAdmin(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}