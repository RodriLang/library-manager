package com.rodrilang.librarymanager.admin.bookstore.controller;

import com.rodrilang.librarymanager.admin.bookstore.dto.request.AdminCreateBookstoreRequest;
import com.rodrilang.librarymanager.admin.bookstore.dto.request.AdminInviteUserRequest;
import com.rodrilang.librarymanager.admin.bookstore.dto.request.AdminUpdateBookstoreRequest;
import com.rodrilang.librarymanager.admin.bookstore.dto.response.AdminBookstoreResponse;
import com.rodrilang.librarymanager.admin.bookstore.service.AdminBookstoreService;
import com.rodrilang.librarymanager.admin.user.dto.AdminUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Administración de librerías", description = "Administración global de librerías de Anaquel")
@RestController
@RequestMapping("/api/admin/bookstores")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookstoreController {

    private final AdminBookstoreService adminBookstoreService;

    @Operation(summary = "Crear librería")
    @PostMapping
    public ResponseEntity<AdminBookstoreResponse> create(
            @RequestBody @Valid AdminCreateBookstoreRequest request
    ) {
        AdminBookstoreResponse response =
                adminBookstoreService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(summary = "Activar librería")
    @PostMapping("/{bookstoreId}/activate")
    public ResponseEntity<Void> activate(
            @PathVariable Long bookstoreId
    ) {
        adminBookstoreService.activate(bookstoreId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Desactivar librería")
    @PostMapping("/{bookstoreId}/deactivate")
    public ResponseEntity<Void> deactivate(
            @PathVariable Long bookstoreId
    ) {
        adminBookstoreService.deactivate(bookstoreId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Invitar usuario a una librería")
    @PostMapping("/{bookstoreId}/invitations")
    public ResponseEntity<Void> inviteUser(
            @PathVariable Long bookstoreId,
            @RequestBody @Valid AdminInviteUserRequest request
    ) {
        adminBookstoreService.inviteUser(
                bookstoreId,
                request
        );

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Listar librerías")
    @GetMapping
    public ResponseEntity<Page<AdminBookstoreResponse>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @ParameterObject
            @PageableDefault(
                    size = 20,
                    sort = "name",
                    direction = Sort.Direction.ASC
            )
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                adminBookstoreService.findAll(
                        search,
                        active,
                        pageable
                )
        );
    }

    @Operation(summary = "Obtener librería")
    @GetMapping("/{bookstoreId}")
    public ResponseEntity<AdminBookstoreResponse> findById(
            @PathVariable Long bookstoreId
    ) {
        return ResponseEntity.ok(
                adminBookstoreService.findById(
                        bookstoreId
                )
        );
    }

    @Operation(summary = "Modificar librería")
    @PatchMapping("/{bookstoreId}")
    public ResponseEntity<AdminBookstoreResponse> update(
            @PathVariable Long bookstoreId,
            @RequestBody @Valid AdminUpdateBookstoreRequest request
    ) {
        return ResponseEntity.ok(
                adminBookstoreService.update(
                        bookstoreId,
                        request
                )
        );
    }

    @Operation(summary = "Listar usuarios de una librería")
    @GetMapping("/{bookstoreId}/users")
    public ResponseEntity<Page<AdminUserResponse>> findUsers(
            @PathVariable Long bookstoreId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean locked,
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                adminBookstoreService.findUsers(
                        bookstoreId,
                        search,
                        enabled,
                        locked,
                        pageable
                )
        );
    }
}