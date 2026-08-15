package com.rodrilang.librarymanager.admin.user.dto;

import com.rodrilang.librarymanager.admin.bookstore.dto.response.AdminBookstoreSummaryResponse;
import com.rodrilang.librarymanager.auth.dtos.response.RoleResponse;

import java.time.Instant;
import java.util.List;

public record AdminUserResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String displayName,
        List<RoleResponse> roles,
        boolean enabled,
        boolean accountLocked,
        AdminBookstoreSummaryResponse bookstore,
        Instant createdAt,
        Instant updatedAt
) {
}