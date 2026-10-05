package com.rodrilang.librarymanager.auth.security.jwt;

import com.rodrilang.librarymanager.auth.security.user.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void separatesRolesPermissionsAndAuthoritiesInToken() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);
        JwtService service = new JwtService(secret, Duration.ofMinutes(15));

        AuthenticatedUser user = new AuthenticatedUser(
                15L,
                7L,
                "bookseller",
                "encoded",
                List.of(
                        new SimpleGrantedAuthority("ROLE_BOOKSTORE_USER"),
                        new SimpleGrantedAuthority("bookstore.inventory.read")
                ),
                true,
                false
        );

        String token = service.generateAccessToken(user);
        Claims claims = parse(token, secret);

        assertThat(claims.get("roles", List.class))
                .containsExactly("ROLE_BOOKSTORE_USER");
        assertThat(claims.get("permissions", List.class))
                .containsExactly("bookstore.inventory.read");
        assertThat(claims.get("authorities", List.class))
                .containsExactly("ROLE_BOOKSTORE_USER", "bookstore.inventory.read");
        assertThat(claims.get("bookstoreId", Number.class).longValue()).isEqualTo(7L);
    }

    @Test
    void supportsPlatformAdminWithoutBookstore() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);
        JwtService service = new JwtService(secret, Duration.ofMinutes(15));

        AuthenticatedUser user = new AuthenticatedUser(
                1L,
                null,
                "admin",
                "encoded",
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("platform.dashboard.read")
                ),
                true,
                false
        );

        Claims claims = parse(service.generateAccessToken(user), secret);

        assertThat(claims.get("roles", List.class)).containsExactly("ROLE_ADMIN");
        assertThat(claims.get("permissions", List.class)).containsExactly("platform.dashboard.read");
        assertThat(claims.get("bookstoreId")).isNull();
    }

    private Claims parse(String token, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
