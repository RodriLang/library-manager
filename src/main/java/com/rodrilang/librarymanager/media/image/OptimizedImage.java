package com.rodrilang.librarymanager.media.image;

public record OptimizedImage(
        byte[] content,
        String filename,
        boolean optimized
) {
}