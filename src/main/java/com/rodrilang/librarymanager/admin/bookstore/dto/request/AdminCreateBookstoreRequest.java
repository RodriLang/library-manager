package com.rodrilang.librarymanager.admin.bookstore.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCreateBookstoreRequest(

        @NotBlank
        @Size(max = 150)
        String name,

        @Email
        @Size(max = 150)
        String email,

        @Size(max = 50)
        String phone,

        @Size(max = 200)
        String address
) {
}