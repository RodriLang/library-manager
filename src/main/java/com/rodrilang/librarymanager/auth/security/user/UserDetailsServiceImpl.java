package com.rodrilang.librarymanager.auth.security.user;

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
import java.util.Locale;
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

        // user_roles queda reservado a roles de plataforma.
        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                addRole(authorities, role);
            }
        }

        Long legacyBookstoreId = user.getBookstore() != null ? user.getBookstore().getId() : null;
        Long activeBookstoreId = requestedBookstoreId != null ? requestedBookstoreId : legacyBookstoreId;

        if (activeBookstoreId != null) {
            membershipRepository.findByUser_IdAndBookstore_Id(user.getId(), activeBookstoreId)
                    .filter(m -> m.isEnabled())
                    .ifPresent(m -> m.getRoles().forEach(role -> addRole(authorities, role)));
        }

        return new AuthenticatedUser(
                user.getId(),
                activeBookstoreId,
                user.getUsername(),
                user.getPassword(),
                authorities,
                user.isEnabled(),
                user.isAccountLocked()
        );
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
}
