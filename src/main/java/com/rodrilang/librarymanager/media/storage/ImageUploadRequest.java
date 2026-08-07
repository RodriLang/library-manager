package com.rodrilang.librarymanager.media.storage;

import java.util.Objects;

public record ImageUploadRequest(
        byte[] content,
        String originalFilename,
        String folder,
        String publicId,
        boolean overwrite
) {

    public ImageUploadRequest {
        Objects.requireNonNull(content, "El contenido de la imagen es obligatorio");

        if (content.length == 0) {
            throw new IllegalArgumentException(
                    "El contenido de la imagen no puede estar vacío"
            );
        }

        if (folder == null || folder.isBlank()) {
            throw new IllegalArgumentException(
                    "La carpeta de destino es obligatoria"
            );
        }
    }

    public static ImageUploadRequest create(
            byte[] content,
            String originalFilename,
            String folder,
            String publicId
    ) {
        return new ImageUploadRequest(
                content,
                originalFilename,
                folder,
                publicId,
                false
        );
    }
}