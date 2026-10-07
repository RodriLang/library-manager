package com.rodrilang.librarymanager.provider.bookstore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBookstoreProviderRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 30) String taxId,
        @Email @Size(max = 160) String email,
        @Size(max = 50) String phone,
        @Size(max = 2000) String notes
) {
}
