package com.rodrilang.librarymanager.auth.controller;

import com.rodrilang.librarymanager.auth.dtos.request.LogoutRequestDto;
import com.rodrilang.librarymanager.auth.dtos.request.RefreshTokenRequest;
import com.rodrilang.librarymanager.auth.dtos.request.UserRequestDto;
import com.rodrilang.librarymanager.auth.dtos.response.AuthResponse;
import com.rodrilang.librarymanager.auth.dtos.response.UserResponse;
import com.rodrilang.librarymanager.auth.services.AuthService;
import com.rodrilang.librarymanager.auth.dtos.request.LoginRequestDto;
import com.rodrilang.librarymanager.auth.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticación", description = "Registro, inicio de sesión y administración de tokens")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "Registrar un usuario")
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody UserRequestDto request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @Operation(summary = "Iniciar sesión")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Cerrar sesión")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequestDto request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Renovar access token")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Obtener el usuario autenticado")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        return ResponseEntity.ok(userService.findByUsername(authentication.getName()));
    }
}