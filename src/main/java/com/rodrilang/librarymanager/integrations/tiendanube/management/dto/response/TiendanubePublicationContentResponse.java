package com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response;

public record TiendanubePublicationContentResponse(
        Long inventoryId,
        Long productId,
        String title,
        String isbn,
        String existingHtml,
        boolean hasDescription,
        String anaquelSynopsis,
        Long variantId,
        com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse variant
) {}
