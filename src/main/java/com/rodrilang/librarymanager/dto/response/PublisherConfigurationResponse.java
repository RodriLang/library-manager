package com.rodrilang.librarymanager.dto.response;

public record PublisherConfigurationResponse(
        Long id,
        String name,
        long bookCount,
        boolean excluded
) {
}