package com.rodrilang.librarymanager.admin.access.controller;

import com.rodrilang.librarymanager.admin.access.dto.AdminRoleResponse;
import com.rodrilang.librarymanager.admin.access.dto.UpdateRolePermissionsRequest;
import com.rodrilang.librarymanager.auth.access.model.Permission;
import com.rodrilang.librarymanager.auth.access.repository.PermissionRepository;
import com.rodrilang.librarymanager.auth.models.Role;
import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.admin.access.dto.AssignPlatformRoleRequest;
import com.rodrilang.librarymanager.auth.access.model.AccessScope;
import com.rodrilang.librarymanager.auth.repositories.RoleRepository;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@RestController
@RequestMapping("/api/admin/access")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccessController {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    @GetMapping("/roles")
    @Transactional(readOnly = true)
    public List<AdminRoleResponse> roles() { return roleRepository.findAll().stream().map(this::toDto).toList(); }

    @GetMapping("/permissions")
    public List<AdminRoleResponse.PermissionItem> permissions() {
        return permissionRepository.findAll().stream().map(p -> new AdminRoleResponse.PermissionItem(p.getId(),p.getCode(),p.getDescription(),p.getScope())).toList();
    }

    @PutMapping("/roles/{roleId}/permissions")
    @Transactional
    public AdminRoleResponse updatePermissions(@PathVariable Long roleId, @Valid @RequestBody UpdateRolePermissionsRequest request) {
        Role role=roleRepository.findById(roleId).orElseThrow(() -> new ResourceNotFoundException("No se encontró el rol."));
        Set<Permission> selected=new LinkedHashSet<>();
        for(String code:request.permissionCodes()) {
            Permission p=permissionRepository.findByCode(code).orElseThrow(() -> new ResourceNotFoundException("No se encontró el permiso "+code+"."));
            if(p.getScope()!=role.getScope()) throw new IllegalArgumentException("El permiso "+code+" no pertenece al ámbito del rol.");
            selected.add(p);
        }
        role.setPermissions(selected);
        return toDto(role);
    }


    @PutMapping("/users/{userId}/platform-role")
    @Transactional
    public void assignPlatformRole(@PathVariable Long userId, @Valid @RequestBody AssignPlatformRoleRequest request) {
        User user = userRepository.findByIdForAdmin(userId).orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario."));
        Role role = roleRepository.findByRoleName(request.role()).orElseThrow(() -> new ResourceNotFoundException("No se encontró el rol."));
        if (role.getScope() != AccessScope.PLATFORM) throw new IllegalArgumentException("El rol no es de ámbito plataforma.");
        user.getRoles().add(role);
    }

    @DeleteMapping("/users/{userId}/platform-role/{roleName}")
    @Transactional
    public void removePlatformRole(@PathVariable Long userId, @PathVariable com.rodrilang.librarymanager.auth.enums.RoleType roleName) {
        User user = userRepository.findByIdForAdmin(userId).orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario."));
        user.getRoles().removeIf(r -> r.getRoleName() == roleName && r.getScope() == AccessScope.PLATFORM);
    }

    private AdminRoleResponse toDto(Role role){
        return new AdminRoleResponse(role.getId(),role.getRoleName(),role.getScope(),role.isSystemRole(),role.getPermissions().stream()
                .sorted(Comparator.comparing(Permission::getCode))
                .map(p -> new AdminRoleResponse.PermissionItem(p.getId(),p.getCode(),p.getDescription(),p.getScope())).toList());
    }
}
