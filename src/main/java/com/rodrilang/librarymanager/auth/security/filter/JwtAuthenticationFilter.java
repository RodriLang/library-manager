package com.rodrilang.librarymanager.auth.security.filter;

import com.rodrilang.librarymanager.auth.security.handler.JwtAuthenticationEntryPoint;
import com.rodrilang.librarymanager.auth.security.jwt.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(BEARER_PREFIX.length());

        try {
            authenticateRequest(jwt, request);
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException exception) {
            SecurityContextHolder.clearContext();

            authenticationEntryPoint.commence(
                    request,
                    response,
                    new CredentialsExpiredException(
                            "El access token ha vencido.",
                            exception
                    )
            );

        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();

            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException(
                            "El access token es inválido.",
                            exception
                    )
            );

        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();

            authenticationEntryPoint.commence(
                    request,
                    response,
                    exception
            );
        }
    }

    private void authenticateRequest(
            String jwt,
            HttpServletRequest request
    ) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        String username = jwtService.extractUsername(jwt);

        if (username == null || username.isBlank()) {
            throw new BadCredentialsException(
                    "El access token no contiene un usuario válido."
            );
        }

        UserDetails userDetails =
                userDetailsService.loadUserByUsername(username);

        if (!jwtService.isTokenValid(jwt, userDetails)) {
            throw new BadCredentialsException(
                    "El access token es inválido."
            );
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

        authentication.setDetails(
                new WebAuthenticationDetailsSource()
                        .buildDetails(request)
        );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);
    }
}