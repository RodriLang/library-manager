package com.rodrilang.librarymanager.dto.response;

public record PublisherConfigurationDetailResponse(
        Long id,
        String name,
        long bookCount,
        boolean excluded
) {
}