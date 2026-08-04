package com.rodrilang.librarymanager.bookstore;

import com.rodrilang.librarymanager.auth.security.user.AuthenticatedUser;
import com.rodrilang.librarymanager.exception.BusinessException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class BookstoreContext {

    public Long getCurrentBookstoreId() {
        return getCurrentUser().bookstoreId();
    }

    public Long getCurrentUserId() {
        return getCurrentUser().userId();
    }

    public AuthenticatedUser getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (
                authentication == null
                        || !authentication.isAuthenticated()
                        || authentication instanceof AnonymousAuthenticationToken
                        || !(authentication.getPrincipal()
                        instanceof AuthenticatedUser authenticatedUser)
        ) {
            throw new BusinessException(
                    "No existe un usuario autenticado."
            );
        }

        if (authenticatedUser.bookstoreId() == null) {
            throw new BusinessException(
                    "El usuario autenticado no tiene una librería asignada."
            );
        }

        return authenticatedUser;
    }
}