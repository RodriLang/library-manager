package com.rodrilang.librarymanager.admin.access.dto;

import com.rodrilang.librarymanager.auth.access.model.AccessScope;
import com.rodrilang.librarymanager.auth.enums.RoleType;
import java.util.List;

public record AdminRoleResponse(Long id, RoleType role, AccessScope scope, boolean systemRole, List<PermissionItem> permissions) {
    public record PermissionItem(Long id, String code, String description, AccessScope scope) {}
}
