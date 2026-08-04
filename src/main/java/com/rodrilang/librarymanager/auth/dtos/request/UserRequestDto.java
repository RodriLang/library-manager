package com.rodrilang.librarymanager.auth.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRequestDto(

        @NotBlank
        String username,

        @NotBlank
        @Size(min = 8, max = 12)
        String password
) {
}
