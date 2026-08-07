package com.rodrilang.librarymanager.media.storage;

public record StoredImage(
        String publicId,
        String secureUrl,
        String format,
        Integer width,
        Integer height,
        Long bytes,
        String resourceType
) {
}