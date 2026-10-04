package com.rodrilang.librarymanager.auth.access.dto;

import com.rodrilang.librarymanager.auth.dtos.response.RoleResponse;
import java.util.List;

public record BookstoreMembershipResponse(
        Long id,
        Long bookstoreId,
        String bookstoreName,
        boolean enabled,
        List<RoleResponse> roles
) {}
