package com.rodrilang.librarymanager.media.validation;

public record ValidatedImage(
        byte[] content,
        String originalFilename,
        ImageContentType contentType
) {
}