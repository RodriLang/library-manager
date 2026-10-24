package com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response;

import java.util.List;

public record TiendanubePublicationQualityResponse(
        Long productId,
        String title,
        boolean published,
        boolean missingIdentifier,
        boolean missingSynopsis,
        boolean missingCover,
        List<String> issues
) {}
