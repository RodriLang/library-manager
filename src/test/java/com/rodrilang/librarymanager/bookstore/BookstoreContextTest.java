package com.rodrilang.librarymanager.bookstore;

import com.rodrilang.librarymanager.auth.access.repository.BookstoreMembershipRepository;
import com.rodrilang.librarymanager.auth.security.user.AuthenticatedUser;
import com.rodrilang.librarymanager.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookstoreContextTest {

    @Mock
    private BookstoreMembershipRepository membershipRepository;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void platformAdminCannotEnterBookstoreWithoutEnabledMembership() {
        AuthenticatedUser admin = new AuthenticatedUser(
                1L,
                null,
                "admin",
                "encoded",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
                true,
                false
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities())
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(BookstoreContext.HEADER, "7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(membershipRepository.existsByUser_IdAndBookstore_IdAndEnabledTrue(1L, 7L))
                .thenReturn(false);

        BookstoreContext context = new BookstoreContext(membershipRepository);

        assertThatThrownBy(context::getCurrentBookstoreId)
                .isInstanceOf(BusinessException.class)
                .hasMessage("No tenés acceso a la librería seleccionada.");
    }
}
