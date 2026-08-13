package com.rodrilang.librarymanager.auth.controller;

import com.rodrilang.librarymanager.auth.dtos.request.ChangePasswordRequest;
import com.rodrilang.librarymanager.auth.dtos.request.UpdateProfileRequest;
import com.rodrilang.librarymanager.auth.dtos.response.UserResponse;
import com.rodrilang.librarymanager.auth.security.user.AuthenticatedUser;
import com.rodrilang.librarymanager.auth.services.UserPasswordService;
import com.rodrilang.librarymanager.auth.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserPasswordService userPasswordService;

    @Operation(summary = "Obtener el usuario autenticado")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return ResponseEntity.ok(
                userService.findById(authenticatedUser.userId())
        );
    }

    @Operation(summary = "Actualizar datos personales")
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody @Valid UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(
                userService.updateProfile(
                        authenticatedUser.userId(),
                        request
                )
        );
    }

    @Operation(summary = "Cambiar contraseña")
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody @Valid ChangePasswordRequest request
    ) {
        userPasswordService.changePassword(
                authenticatedUser.userId(),
                request.currentPassword(),
                request.newPassword()
        );

        return ResponseEntity.noContent().build();
    }
}