package com.rodrilang.librarymanager.auth.security.jwt;

import com.rodrilang.librarymanager.auth.security.user.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration jwtExpiration;

    public JwtService(
            @Value("${jwt.secret}") String jwtSecretKey,
            @Value("${jwt.expiration}") Duration jwtExpiration
    ) {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecretKey);

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.jwtExpiration = jwtExpiration;
    }

    public String generateAccessToken(UserDetails userDetails) {
        if (!(userDetails instanceof AuthenticatedUser authenticatedUser)) {
            throw new IllegalArgumentException(
                    "El usuario autenticado no es un AuthenticatedUser."
            );
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(jwtExpiration);

        List<String> roles = authenticatedUser.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .subject(authenticatedUser.getUsername())
                .claim("userId", authenticatedUser.userId())
                .claim("bookstoreId", authenticatedUser.bookstoreId())
                .claim("roles", roles)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(
            String token,
            UserDetails userDetails
    ) {
        String username = extractUsername(token);

        return username.equals(userDetails.getUsername())
                && !isTokenExpired(token)
                && userDetails.isAccountNonLocked()
                && userDetails.isAccountNonExpired()
                && userDetails.isCredentialsNonExpired()
                && userDetails.isEnabled();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        Instant expiration = extractAllClaims(token)
                .getExpiration()
                .toInstant();

        return expiration.isBefore(Instant.now());
    }
}