package com.rodrilang.librarymanager.auth.security.user;

import com.rodrilang.librarymanager.auth.models.User;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

@NullMarked
public record CustomUserDetails(User user) implements UserDetails {

    private static final String ROLE_PREFIX = "ROLE_";

    public Long getId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        return user.getRoles()
                .stream()
                .map(role -> new SimpleGrantedAuthority(
                        ROLE_PREFIX + role.getRoleName().name()
                ))
                .toList();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

}