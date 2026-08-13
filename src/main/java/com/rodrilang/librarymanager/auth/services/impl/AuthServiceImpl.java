package com.rodrilang.librarymanager.auth.services.impl;

import com.rodrilang.librarymanager.auth.dtos.internal.RefreshTokenRotationResult;
import com.rodrilang.librarymanager.auth.dtos.request.LoginRequestDto;
import com.rodrilang.librarymanager.auth.dtos.request.LogoutRequestDto;
import com.rodrilang.librarymanager.auth.dtos.request.RefreshTokenRequest;
import com.rodrilang.librarymanager.auth.dtos.request.UserRequestDto;
import com.rodrilang.librarymanager.auth.dtos.response.AuthResponse;
import com.rodrilang.librarymanager.auth.dtos.response.UserResponse;
import com.rodrilang.librarymanager.auth.security.jwt.JwtService;
import com.rodrilang.librarymanager.auth.services.AuthService;
import com.rodrilang.librarymanager.auth.services.RefreshTokenService;
import com.rodrilang.librarymanager.auth.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserDetailsService userDetailsService;

    @Override
    @Transactional
    public UserResponse register(UserRequestDto request) {
        return userService.createUser(request);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequestDto request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.identifier(),
                        request.password()
                )
        );

        UserDetails userDetails = extractUserDetails(authentication);

        String username = userDetails.getUsername();

        String accessToken = jwtService.generateAccessToken(userDetails);

        String refreshToken = refreshTokenService.generateRefreshToken(username);

        UserResponse user = userService.findByUsername(username);

        return new AuthResponse(
                accessToken,
                refreshToken,
                user
        );
    }

    @Override
    @Transactional
    public void logout(LogoutRequestDto request) {
        refreshTokenService.revokeToken(request.refreshToken());
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenRotationResult rotation =
                refreshTokenService.rotate(request.refreshToken());

        UserDetails userDetails = userDetailsService.loadUserByUsername(rotation.username());

        String accessToken = jwtService.generateAccessToken(userDetails);

        UserResponse user = userService.findByUsername(rotation.username());

        return new AuthResponse(accessToken, rotation.refreshToken(), user);
    }

    private UserDetails extractUserDetails(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails userDetails) {
            return userDetails;
        }

        throw new IllegalStateException("El principal autenticado no implementa UserDetails.");
    }
}