package com.rodrilang.librarymanager.admin.catalog.dto;

import java.time.Instant;
import java.util.List;

public record AdminCatalogBookResponse(
        Long id,
        String title,
        String isbn,
        String publisher,
        String authors,
        String coverUrl,
        Instant updatedAt,
        List<String> issues
) {}
