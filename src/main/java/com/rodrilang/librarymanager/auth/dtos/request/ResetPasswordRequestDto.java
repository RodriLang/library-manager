package com.rodrilang.librarymanager.auth.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequestDto(

        @NotBlank(message = "Recovery Token is required")
        String recoveryToken,

        @NotBlank(message = "New Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String newPassword
) {
}
