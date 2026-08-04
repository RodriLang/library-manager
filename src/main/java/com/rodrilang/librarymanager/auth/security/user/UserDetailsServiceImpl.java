package com.rodrilang.librarymanager.auth.security.user;

import com.rodrilang.librarymanager.auth.models.Role;
import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser loadUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "El usuario o la contraseña son incorrectos."
                        )
                );

        if (user.getBookstore() == null) {
            throw new IllegalStateException(
                    "El usuario no tiene una librería asignada."
            );
        }

        List<SimpleGrantedAuthority> authorities =
                user.getRoles() == null
                        ? List.of()
                        : user.getRoles()
                        .stream()
                        .map(Role::getRoleName)
                        .map(role ->
                             new SimpleGrantedAuthority(
                                     "ROLE_" + role.name()
                             )
                        )
                        .toList();

        return new AuthenticatedUser(
                user.getId(),
                user.getBookstore().getId(),
                user.getUsername(),
                user.getPassword(),
                authorities,
                user.isEnabled(),
                user.isAccountLocked()
        );
    }
}