package com.rodrilang.librarymanager.provider.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProviderRequest(

        @NotBlank
        @Size(max = 150)
        String name,

        @Size(max = 30)
        String taxId,

        @Email
        @Size(max = 160)
        String email,

        @Size(max = 50)
        String phone,

        String notes,

        Boolean active

) {
}
