package com.rodrilang.librarymanager.bookstore;

import com.rodrilang.librarymanager.auth.access.repository.BookstoreMembershipRepository;
import com.rodrilang.librarymanager.auth.security.user.AuthenticatedUser;
import com.rodrilang.librarymanager.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
public class BookstoreContext {
    public static final String HEADER = "X-Bookstore-Id";
    private final BookstoreMembershipRepository membershipRepository;

    public Long getCurrentBookstoreId() {
        AuthenticatedUser user = getCurrentUser();
        Long requested = requestedBookstoreId();
        Long bookstoreId = requested != null ? requested : user.bookstoreId();
        if (bookstoreId == null) throw new BusinessException("Seleccioná una librería para realizar esta operación.");

        boolean platformAdmin = user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!platformAdmin && !membershipRepository.existsByUser_IdAndBookstore_IdAndEnabledTrue(user.userId(), bookstoreId)) {
            throw new BusinessException("No tenés acceso a la librería seleccionada.");
        }
        return bookstoreId;
    }

    public Long getCurrentUserId() { return getCurrentUser().userId(); }

    public AuthenticatedUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser)) {
            throw new BusinessException("No existe un usuario autenticado.");
        }
        return authenticatedUser;
    }

    private Long requestedBookstoreId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return null;
        HttpServletRequest request = attrs.getRequest();
        String value = request.getHeader(HEADER);
        if (value == null || value.isBlank()) return null;
        try { return Long.valueOf(value); }
        catch (NumberFormatException ex) { throw new BusinessException("La librería seleccionada no es válida."); }
    }
}
