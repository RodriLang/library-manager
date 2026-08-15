package com.rodrilang.librarymanager.admin.bookstore.dto.request;

import com.rodrilang.librarymanager.auth.enums.RoleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminInviteUserRequest(

        @NotBlank
        @Email
        String email,

        @NotNull
        RoleType role

) {
}