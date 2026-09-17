package com.rodrilang.librarymanager.purchasing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupplierRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 30) String taxId,
        @Size(max = 160) String email,
        @Size(max = 50) String phone,
        String notes
) {
}
