package com.rodrilang.librarymanager.auth.security.user;

import com.rodrilang.librarymanager.auth.access.model.AccessScope;
import com.rodrilang.librarymanager.auth.access.model.BookstoreMembership;
import com.rodrilang.librarymanager.auth.access.model.Permission;
import com.rodrilang.librarymanager.auth.access.repository.BookstoreMembershipRepository;
import com.rodrilang.librarymanager.auth.enums.RoleType;
import com.rodrilang.librarymanager.auth.models.Role;
import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.model.Bookstore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookstoreMembershipRepository membershipRepository;

    @InjectMocks
    private UserDetailsServiceImpl service;

    @Test
    void loadsPlatformAdminWithoutBookstore() {
        Role admin = role(
                RoleType.ADMIN,
                AccessScope.PLATFORM,
                permission("platform.dashboard.read", AccessScope.PLATFORM)
        );
        User user = user(Set.of(admin));

        when(userRepository.findByUsernameOrEmail("admin")).thenReturn(Optional.of(user));
        when(membershipRepository.findAllByUser_IdAndEnabledTrueOrderByBookstore_NameAsc(1L))
                .thenReturn(List.of());

        AuthenticatedUser authenticated = service.loadUserByUsername("ADMIN");

        assertThat(authenticated.bookstoreId()).isNull();
        assertThat(authorityNames(authenticated))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "platform.dashboard.read");
    }

    @Test
    void automaticallySelectsSingleEnabledBookstoreMembership() {
        Role bookstoreUser = role(
                RoleType.BOOKSTORE_USER,
                AccessScope.BOOKSTORE,
                permission("bookstore.inventory.read", AccessScope.BOOKSTORE)
        );
        User user = user(Set.of());
        Bookstore bookstore = mock(Bookstore.class);
        when(bookstore.getId()).thenReturn(7L);

        BookstoreMembership membership = BookstoreMembership.builder()
                .id(10L)
                .user(user)
                .bookstore(bookstore)
                .roles(Set.of(bookstoreUser))
                .enabled(true)
                .build();

        when(userRepository.findByUsernameOrEmail("bookseller")).thenReturn(Optional.of(user));
        when(membershipRepository.findAllByUser_IdAndEnabledTrueOrderByBookstore_NameAsc(1L))
                .thenReturn(List.of(membership));

        AuthenticatedUser authenticated = service.loadUserByUsername("bookseller");

        assertThat(authenticated.bookstoreId()).isEqualTo(7L);
        assertThat(authorityNames(authenticated))
                .containsExactlyInAnyOrder("ROLE_BOOKSTORE_USER", "bookstore.inventory.read");
    }

    @Test
    void doesNotChooseArbitraryBookstoreWhenSeveralMembershipsAreEnabled() {
        User user = user(Set.of());
        Bookstore firstBookstore = mock(Bookstore.class);
        Bookstore secondBookstore = mock(Bookstore.class);

        BookstoreMembership first = BookstoreMembership.builder()
                .id(10L)
                .user(user)
                .bookstore(firstBookstore)
                .enabled(true)
                .build();
        BookstoreMembership second = BookstoreMembership.builder()
                .id(11L)
                .user(user)
                .bookstore(secondBookstore)
                .enabled(true)
                .build();

        when(userRepository.findByUsernameOrEmail("multi")).thenReturn(Optional.of(user));
        when(membershipRepository.findAllByUser_IdAndEnabledTrueOrderByBookstore_NameAsc(1L))
                .thenReturn(List.of(first, second));

        AuthenticatedUser authenticated = service.loadUserByUsername("multi");

        assertThat(authenticated.bookstoreId()).isNull();
        assertThat(authenticated.getAuthorities()).isEmpty();
    }

    private User user(Set<Role> roles) {
        return User.builder()
                .id(1L)
                .username("user")
                .password("encoded")
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .roles(roles)
                .enabled(true)
                .accountLocked(false)
                .build();
    }

    private Role role(RoleType type, AccessScope scope, Permission... permissions) {
        return Role.builder()
                .id(1L)
                .roleName(type)
                .scope(scope)
                .permissions(Set.of(permissions))
                .build();
    }

    private Permission permission(String code, AccessScope scope) {
        return Permission.builder()
                .id(1L)
                .code(code)
                .description(code)
                .scope(scope)
                .build();
    }

    private Set<String> authorityNames(AuthenticatedUser user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
    }
}
