package com.rodrilang.librarymanager.auth.dtos.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(

        @NotBlank
        String identifier,

        @NotBlank
        String password
) {
}
