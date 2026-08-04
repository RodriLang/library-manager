package com.rodrilang.librarymanager.auth.dtos.request;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequestDto(

        @NotBlank
        String refreshToken
) {
}
