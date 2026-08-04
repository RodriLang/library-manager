package com.rodrilang.librarymanager.auth.dtos.response;

import java.util.List;

public record UserResponse(

        Long id,

        String username,

        List<RoleResponse> roles,

        boolean enabled,

        BookstoreAuthResponse bookstore
) {
}
