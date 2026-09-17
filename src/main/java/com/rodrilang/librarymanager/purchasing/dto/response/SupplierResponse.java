package com.rodrilang.librarymanager.purchasing.dto.response;

public record SupplierResponse(
        Long id,
        String name,
        String taxId,
        String email,
        String phone,
        String notes,
        boolean active
) {
}
