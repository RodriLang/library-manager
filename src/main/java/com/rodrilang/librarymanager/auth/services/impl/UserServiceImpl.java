package com.rodrilang.librarymanager.auth.services.impl;

import com.rodrilang.librarymanager.auth.dtos.request.UpdateProfileRequest;
import com.rodrilang.librarymanager.auth.dtos.request.UserRequestDto;
import com.rodrilang.librarymanager.auth.dtos.response.UserResponse;
import com.rodrilang.librarymanager.auth.enums.RoleType;
import com.rodrilang.librarymanager.auth.mappers.UserMapper;
import com.rodrilang.librarymanager.auth.models.Role;
import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.auth.services.RoleService;
import com.rodrilang.librarymanager.auth.services.UserService;
import com.rodrilang.librarymanager.exception.DuplicateResourceException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.invitation.dto.InvitationRegisterRequest;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.service.BookstoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final BookstoreService bookstoreService;

    private static final String USER_NOT_FOUND_MESSAGE = "No se encontró el usuario autenticado.";

    @Override
    @Transactional
    public UserResponse createUser(UserRequestDto request) {
        String normalizedUsername = normalize(request.username());
        String normalizedEmail = normalize(request.email());

        validateUsername(normalizedUsername);
        validateEmail(normalizedEmail);

        User user = userMapper.toEntity(request);
        Bookstore bookstore = bookstoreService.getEntityById(request.bookstoreId());

        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setBookstore(bookstore);

        Role role = roleService.findByName(RoleType.BOOKSTORE_ADMIN);
        user.setRoles(Set.of(role));

        User savedUser = userRepository.save(user);

        return userMapper.toDto(savedUser);
    }

    @Override
    @Transactional
    public UserResponse createUserFromInvitation(
            InvitationRegisterRequest request,
            Bookstore bookstore,
            RoleType roleType) {

        String normalizedUsername = normalize(request.username());
        String normalizedEmail = normalize(request.email());

        validateUsername(normalizedUsername);
        validateEmail(normalizedEmail);

        User user = new User();

        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setBookstore(bookstore);

        Role role = roleService.findByName(roleType);
        user.setRoles(Set.of(role));

        User savedUser = userRepository.save(user);

        return userMapper.toDto(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long userId) {
        User user = userRepository
                .findByIdAndEnabledTrueAndAccountLockedFalse(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_MESSAGE));

        return userMapper.toDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findByUsername(String identifier) {
        String normalized = normalize(identifier);

        User user = userRepository.findByUsernameOrEmail(normalized)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_MESSAGE));

        return userMapper.toDto(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(
            Long userId,
            UpdateProfileRequest request
    ) {
        User user = userRepository.findByIdAndEnabledTrueAndAccountLockedFalse(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_MESSAGE));

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());

        return userMapper.toDto(user);
    }

    private void validateUsername(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Ya existe un usuario registrado como " + username);
        }
    }

    private void validateEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Ya existe un usuario registrado con el correo " + email);
        }
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}