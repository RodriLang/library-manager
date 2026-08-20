package com.rodrilang.librarymanager.auth.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @NotBlank
        @Size(max = 80)
        String firstName,

        @NotBlank
        @Size(max = 80)
        String lastName

) {
}