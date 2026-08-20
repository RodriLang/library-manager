package com.rodrilang.librarymanager.admin.bookstore.dto.response;

public record AdminBookstoreSummaryResponse(
        Long id,
        String name,
        Boolean active
) {
}