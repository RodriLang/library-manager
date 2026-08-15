package com.rodrilang.librarymanager.admin.user.controller;

import com.rodrilang.librarymanager.admin.user.dto.AdminUpdateUserRequest;
import com.rodrilang.librarymanager.admin.user.dto.AdminUserResponse;
import com.rodrilang.librarymanager.admin.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Administración de usuarios", description = "Administración global de usuarios de Anaquel")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "Listar usuarios")
    @GetMapping
    public ResponseEntity<Page<AdminUserResponse>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean locked,
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                adminUserService.findAll(
                        search,
                        enabled,
                        locked,
                        pageable
                )
        );
    }

    @Operation(summary = "Obtener usuario")
    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserResponse> findById(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(adminUserService.findById(userId));
    }

    @Operation(summary = "Modificar usuario")
    @PatchMapping("/{userId}")
    public ResponseEntity<AdminUserResponse> update(
            @PathVariable Long userId,
            @RequestBody @Valid AdminUpdateUserRequest request
    ) {
        return ResponseEntity.ok(adminUserService.updateUser(userId, request));
    }

    @Operation(summary = "Bloquear usuario")
    @PostMapping("/{userId}/lock")
    public ResponseEntity<Void> lock(
            @PathVariable Long userId
    ) {
        adminUserService.lock(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Desbloquear usuario")
    @PostMapping("/{userId}/unlock")
    public ResponseEntity<Void> unlock(
            @PathVariable Long userId
    ) {
        adminUserService.unlock(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Deshabilitar usuario")
    @PostMapping("/{userId}/disable")
    public ResponseEntity<Void> disable(
            @PathVariable Long userId
    ) {
        adminUserService.disable(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Habilitar usuario")
    @PostMapping("/{userId}/enable")
    public ResponseEntity<Void> enable(
            @PathVariable Long userId
    ) {
        adminUserService.enable(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Cerrar todas las sesiones del usuario")
    @PostMapping("/{userId}/revoke-sessions")
    public ResponseEntity<Void> revokeSessions(
            @PathVariable Long userId
    ) {
        adminUserService.revokeSessions(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Enviar recuperación de contraseña")
    @PostMapping("/{userId}/password-reset")
    public ResponseEntity<Void> sendPasswordReset(
            @PathVariable Long userId
    ) {
        adminUserService.sendPasswordReset(userId);
        return ResponseEntity.noContent().build();
    }
}