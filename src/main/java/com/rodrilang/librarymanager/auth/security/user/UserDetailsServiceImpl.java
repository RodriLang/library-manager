package com.rodrilang.librarymanager.auth.security.user;

import com.rodrilang.librarymanager.auth.access.model.AccessScope;
import com.rodrilang.librarymanager.auth.access.model.BookstoreMembership;
import com.rodrilang.librarymanager.auth.access.repository.BookstoreMembershipRepository;
import com.rodrilang.librarymanager.auth.models.Role;
import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final BookstoreMembershipRepository membershipRepository;

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser loadUserByUsername(String identifier) {
        return loadUserByUsername(identifier, null);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser loadUserByUsername(String identifier, Long requestedBookstoreId) {
        String normalizedIdentifier = normalize(identifier);
        User user = userRepository.findByUsernameOrEmail(normalizedIdentifier)
                .orElseThrow(() -> new UsernameNotFoundException("El usuario o la contraseña son incorrectos."));

        Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();

        // user_roles contiene únicamente roles globales/de plataforma.
        if (user.getRoles() != null) {
            user.getRoles().stream()
                    .filter(role -> role.getScope() == AccessScope.PLATFORM)
                    .forEach(role -> addRole(authorities, role));
        }

        ResolvedBookstoreAccess bookstoreAccess = resolveBookstoreAccess(user, requestedBookstoreId);
        bookstoreAccess.membership()
                .ifPresent(membership -> membership.getRoles().stream()
                        .filter(role -> role.getScope() == AccessScope.BOOKSTORE)
                        .forEach(role -> addRole(authorities, role)));

        return new AuthenticatedUser(
                user.getId(),
                bookstoreAccess.bookstoreId(),
                user.getUsername(),
                user.getPassword(),
                authorities,
                user.isEnabled(),
                user.isAccountLocked()
        );
    }

    /**
     * Resuelve el contexto de librería sin obligar a los usuarios de plataforma a pertenecer a una.
     *
     * Orden de resolución:
     * 1. Librería solicitada explícitamente (X-Bookstore-Id).
     * 2. Librería legacy del usuario, siempre que siga teniendo una membresía habilitada.
     * 3. Si existe una única membresía habilitada, se selecciona automáticamente.
     * 4. Con cero o varias membresías y sin selección explícita, no hay librería activa.
     */
    private ResolvedBookstoreAccess resolveBookstoreAccess(User user, Long requestedBookstoreId) {
        if (requestedBookstoreId != null) {
            Optional<BookstoreMembership> requestedMembership = membershipRepository
                    .findByUser_IdAndBookstore_Id(user.getId(), requestedBookstoreId)
                    .filter(BookstoreMembership::isEnabled);

            // Un ADMIN global puede seleccionar una librería aunque no tenga membresía propia.
            return new ResolvedBookstoreAccess(requestedBookstoreId, requestedMembership);
        }

        Long legacyBookstoreId = user.getBookstore() != null ? user.getBookstore().getId() : null;
        if (legacyBookstoreId != null) {
            Optional<BookstoreMembership> legacyMembership = membershipRepository
                    .findByUser_IdAndBookstore_Id(user.getId(), legacyBookstoreId)
                    .filter(BookstoreMembership::isEnabled);

            if (legacyMembership.isPresent()) {
                return new ResolvedBookstoreAccess(legacyBookstoreId, legacyMembership);
            }
        }

        List<BookstoreMembership> enabledMemberships = membershipRepository
                .findAllByUser_IdAndEnabledTrueOrderByBookstore_NameAsc(user.getId());

        if (enabledMemberships.size() == 1) {
            BookstoreMembership membership = enabledMemberships.getFirst();
            return new ResolvedBookstoreAccess(
                    membership.getBookstore().getId(),
                    Optional.of(membership)
            );
        }

        return new ResolvedBookstoreAccess(null, Optional.empty());
    }

    private void addRole(Set<SimpleGrantedAuthority> authorities, Role role) {
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleName().name()));
        role.getPermissions().forEach(permission ->
                authorities.add(new SimpleGrantedAuthority(permission.getCode()))
        );
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record ResolvedBookstoreAccess(
            Long bookstoreId,
            Optional<BookstoreMembership> membership
    ) {
    }
}
