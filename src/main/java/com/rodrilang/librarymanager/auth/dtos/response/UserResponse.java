package com.rodrilang.librarymanager.auth.dtos.response;

import com.rodrilang.librarymanager.auth.access.dto.BookstoreMembershipResponse;
import java.util.List;

public record UserResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String displayName,
        List<RoleResponse> roles,
        boolean enabled,
        BookstoreAuthResponse bookstore,
        List<BookstoreMembershipResponse> memberships
) {
}