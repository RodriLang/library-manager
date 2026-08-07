package com.rodrilang.librarymanager.media.download;

public record DownloadedImage(
        byte[] content,
        String filename,
        String declaredContentType,
        String finalUrl
) {

    public DownloadedImage {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException(
                    "La imagen descargada no puede estar vacía"
            );
        }
    }
}