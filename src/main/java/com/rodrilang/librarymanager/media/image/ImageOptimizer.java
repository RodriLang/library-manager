package com.rodrilang.librarymanager.media.image;

public interface ImageOptimizer {

    OptimizedImage optimize(
            byte[] content,
            String filename,
            String contentType
    );
}