package com.rodrilang.librarymanager.admin.bookstore.dto.response;

import java.time.Instant;

public record AdminBookstoreResponse(
        Long id,
        String name,
        String email,
        String phone,
        String address,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}