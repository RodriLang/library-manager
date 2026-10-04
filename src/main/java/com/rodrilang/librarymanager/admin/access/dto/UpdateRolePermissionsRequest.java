package com.rodrilang.librarymanager.admin.access.dto;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
public record UpdateRolePermissionsRequest(@NotNull Set<String> permissionCodes) {}
