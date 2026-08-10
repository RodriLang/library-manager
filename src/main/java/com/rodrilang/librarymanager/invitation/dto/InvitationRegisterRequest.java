
package com.rodrilang.librarymanager.invitation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InvitationRegisterRequest(

        @NotBlank
        String username,

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(max = 80)
        String firstName,

        @NotBlank
        @Size(max = 80)
        String lastName,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotNull
        Long bookstoreId

) {
}