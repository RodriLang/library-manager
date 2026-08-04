package com.rodrilang.librarymanager.auth.controller;

import com.rodrilang.librarymanager.auth.dtos.request.RoleRequestDto;
import com.rodrilang.librarymanager.auth.dtos.response.RoleResponse;
import com.rodrilang.librarymanager.auth.services.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Tag(name = "Roles", description = "Administración de roles del sistema")
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "Listar roles")
    @GetMapping
    public List<RoleResponse> findAll() {
        return roleService.findAll();
    }

    @Operation(summary = "Obtener rol por id")
    @GetMapping("/{id}")
    public RoleResponse findById(@PathVariable Long id) {
        return roleService.findById(id);
    }

    @Operation(summary = "Crear rol")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse create(@Valid @RequestBody RoleRequestDto request) {
        return roleService.create(request);
    }
}