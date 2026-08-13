package com.rodrilang.librarymanager.auth.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailRequestDto(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email
) {
}
