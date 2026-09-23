package com.rodrilang.librarymanager.auth.access.service;

import com.rodrilang.librarymanager.auth.access.dto.BookstoreMembershipResponse;
import com.rodrilang.librarymanager.auth.access.model.BookstoreMembership;
import com.rodrilang.librarymanager.auth.access.repository.BookstoreMembershipRepository;
import com.rodrilang.librarymanager.auth.enums.RoleType;
import com.rodrilang.librarymanager.auth.models.Role;
import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.auth.services.RoleService;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BookstoreMembershipService {
    private final BookstoreMembershipRepository repository;
    private final UserRepository userRepository;
    private final BookstoreRepository bookstoreRepository;
    private final RoleService roleService;

    @Transactional(readOnly = true)
    public List<BookstoreMembershipResponse> findForUser(Long userId) {
        return repository.findAllByUser_IdOrderByBookstore_NameAsc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public BookstoreMembershipResponse assign(Long userId, Long bookstoreId, RoleType roleType) {
        if (roleType != RoleType.BOOKSTORE_ADMIN && roleType != RoleType.BOOKSTORE_USER) {
            throw new IllegalArgumentException("El rol debe ser de ámbito librería.");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario."));
        Bookstore bookstore = bookstoreRepository.findById(bookstoreId).orElseThrow(() -> new ResourceNotFoundException("No se encontró la librería."));
        Role role = roleService.findByName(roleType);
        BookstoreMembership membership = repository.findByUser_IdAndBookstore_Id(userId, bookstoreId)
                .orElseGet(() -> BookstoreMembership.builder().user(user).bookstore(bookstore).build());
        membership.setEnabled(true);
        membership.setRoles(Set.of(role));
        return toResponse(repository.save(membership));
    }

    @Transactional
    public void remove(Long userId, Long bookstoreId) {
        repository.findByUser_IdAndBookstore_Id(userId, bookstoreId).ifPresent(repository::delete);
    }

    @Transactional
    public BookstoreMembershipResponse setEnabled(Long userId, Long bookstoreId, boolean enabled) {
        BookstoreMembership membership = repository.findByUser_IdAndBookstore_Id(userId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la asociación usuario-librería."));
        membership.setEnabled(enabled);
        return toResponse(membership);
    }

    private BookstoreMembershipResponse toResponse(BookstoreMembership membership) {
        return new BookstoreMembershipResponse(
                membership.getId(), membership.getBookstore().getId(), membership.getBookstore().getName(), membership.isEnabled(),
                membership.getRoles().stream()
                        .map(role -> new com.rodrilang.librarymanager.auth.dtos.response.RoleResponse(role.getRoleName()))
                        .toList()
        );
    }
}
