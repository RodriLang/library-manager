package com.rodrilang.librarymanager.inventory.image.dto;

public record InventoryImageResponse(
        Long id,
        String url,
        String source,
        Integer order,
        boolean primary,
        boolean inheritedFromBookCover,
        Integer width,
        Integer height,
        Long fileSize
) {
}
