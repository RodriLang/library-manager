package com.rodrilang.librarymanager.admin.bookstore.service.impl;

import com.rodrilang.librarymanager.admin.bookstore.dto.request.AdminCreateBookstoreRequest;
import com.rodrilang.librarymanager.admin.bookstore.dto.request.AdminInviteUserRequest;
import com.rodrilang.librarymanager.admin.bookstore.dto.request.AdminUpdateBookstoreRequest;
import com.rodrilang.librarymanager.admin.bookstore.dto.response.AdminBookstoreResponse;
import com.rodrilang.librarymanager.admin.bookstore.mapper.AdminBookstoreMapper;
import com.rodrilang.librarymanager.admin.bookstore.service.AdminBookstoreService;
import com.rodrilang.librarymanager.admin.bookstore.specification.BookstoreSpecifications;
import com.rodrilang.librarymanager.admin.user.dto.AdminUserResponse;
import com.rodrilang.librarymanager.admin.user.mapper.AdminUserMapper;
import com.rodrilang.librarymanager.admin.user.specification.UserSpecifications;
import com.rodrilang.librarymanager.auth.enums.RoleType;
import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.auth.services.RefreshTokenService;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.invitation.dto.CreateBookstoreInvitationRequest;
import com.rodrilang.librarymanager.invitation.services.BookstoreInvitationService;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminBookstoreServiceImpl implements AdminBookstoreService {

    private static final String BOOKSTORE_NOT_FOUND = "No se encontró la librería.";

    private final BookstoreRepository bookstoreRepository;
    private final UserRepository userRepository;
    private final AdminBookstoreMapper bookstoreMapper;
    private final AdminUserMapper userMapper;
    private final BookstoreInvitationService invitationService;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminBookstoreResponse> findAll(
            String search,
            Boolean active,
            Pageable pageable
    ) {
        Specification<Bookstore> specification =
                BookstoreSpecifications.matchesSearch(search)
                        .and(BookstoreSpecifications.hasActive(active));

        return bookstoreRepository
                .findAll(specification, pageable)
                .map(bookstoreMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminBookstoreResponse findById(Long bookstoreId) {
        return bookstoreMapper.toDto(getBookstore(bookstoreId));
    }

    @Override
    @Transactional
    public AdminBookstoreResponse create(AdminCreateBookstoreRequest request) {
        Bookstore bookstore = Bookstore.builder()
                .name(request.name().trim())
                .email(normalizeNullable(request.email()))
                .phone(trimNullable(request.phone()))
                .address(trimNullable(request.address()))
                .active(true)
                .build();

        Bookstore saved =
                bookstoreRepository.save(bookstore);

        return bookstoreMapper.toDto(saved);
    }

    @Override
    @Transactional
    public AdminBookstoreResponse update(Long bookstoreId, AdminUpdateBookstoreRequest request) {
        Bookstore bookstore = getBookstore(bookstoreId);

        bookstore.setName(request.name().trim());
        bookstore.setEmail(normalizeNullable(request.email()));
        bookstore.setPhone(trimNullable(request.phone()));
        bookstore.setAddress(trimNullable(request.address()));

        return bookstoreMapper.toDto(bookstore);
    }

    @Override
    @Transactional
    public void activate(Long bookstoreId) {
        Bookstore bookstore = getBookstore(bookstoreId);

        if (Boolean.TRUE.equals(bookstore.getActive())) {
            return;
        }

        bookstore.setActive(true);
    }

    @Override
    @Transactional
    public void deactivate(Long bookstoreId) {
        Bookstore bookstore = getBookstore(bookstoreId);

        if (Boolean.FALSE.equals(bookstore.getActive())) {
            return;
        }

        bookstore.setActive(false);

        refreshTokenService.revokeAllForBookstore(bookstore.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> findUsers(
            Long bookstoreId,
            String search,
            Boolean enabled,
            Boolean locked,
            Pageable pageable
    ) {
        getBookstore(bookstoreId);

        Specification<User> specification =
                UserSpecifications.belongsToBookstore(bookstoreId)
                        .and(UserSpecifications.matchesSearch(search))
                        .and(UserSpecifications.hasEnabled(enabled))
                        .and(UserSpecifications.hasLocked(locked));

        return userRepository
                .findAll(specification, pageable)
                .map(userMapper::toDto);
    }

    @Override
    @Transactional
    public void inviteUser(Long bookstoreId, AdminInviteUserRequest request) {

        validateBookstoreRole(request.role());

        invitationService.create(
                new CreateBookstoreInvitationRequest(
                        bookstoreId,
                        request.email(),
                        request.role(),
                        null
                )
        );
    }

    private Bookstore getBookstore(Long bookstoreId) {
        return bookstoreRepository
                .findById(bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException(BOOKSTORE_NOT_FOUND));
    }

    private void validateBookstoreRole(RoleType role) {
        if (role != RoleType.BOOKSTORE_ADMIN
                && role != RoleType.BOOKSTORE_USER) {
            throw new BusinessException("El rol no es válido para una librería.");
        }
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String trimNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}