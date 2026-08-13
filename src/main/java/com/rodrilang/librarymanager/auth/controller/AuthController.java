package com.rodrilang.librarymanager.auth.controller;

import com.rodrilang.librarymanager.auth.dtos.request.EmailRequestDto;
import com.rodrilang.librarymanager.auth.dtos.request.LoginRequestDto;
import com.rodrilang.librarymanager.auth.dtos.request.LogoutRequestDto;
import com.rodrilang.librarymanager.auth.dtos.request.RefreshTokenRequest;
import com.rodrilang.librarymanager.auth.dtos.request.ResetPasswordRequestDto;
import com.rodrilang.librarymanager.auth.dtos.response.AuthResponse;
import com.rodrilang.librarymanager.auth.services.AuthService;
import com.rodrilang.librarymanager.auth.services.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Autenticación",
        description = "Inicio de sesión, recuperación de contraseña y administración de tokens"
)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @Operation(
            summary = "Iniciar sesión",
            description = "Permite iniciar sesión utilizando nombre de usuario o correo electrónico"
    )
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody @Valid LoginRequestDto request
    ) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Cerrar sesión")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody @Valid LogoutRequestDto request
    ) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Renovar access token")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @RequestBody @Valid RefreshTokenRequest request
    ) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @Operation(
            summary = "Solicitar recuperación de contraseña",
            description = "Envía al correo del usuario un enlace para restablecer la contraseña"
    )
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(
            @RequestBody @Valid EmailRequestDto request
    ) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Restablecer contraseña",
            description = "Establece una nueva contraseña utilizando un token de recuperación válido"
    )
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @RequestBody @Valid ResetPasswordRequestDto request
    ) {
        passwordResetService.resetPassword(
                request.recoveryToken(),
                request.newPassword()
        );

        return ResponseEntity.noContent().build();
    }
}