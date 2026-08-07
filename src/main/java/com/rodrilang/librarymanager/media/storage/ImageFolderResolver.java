package com.rodrilang.librarymanager.media.storage;

import com.rodrilang.librarymanager.media.configuration.CloudinaryProperties;
import org.springframework.stereotype.Component;

@Component
public class ImageFolderResolver {

    private final String rootFolder;

    public ImageFolderResolver(CloudinaryProperties properties) {
        this.rootFolder = normalizeSegment(properties.rootFolder());
    }

    public String bookCovers(Long bookId) {
        if (bookId == null) {
            throw new IllegalArgumentException(
                    "El id del libro es obligatorio"
            );
        }

        return "%s/books/%d/covers".formatted(rootFolder, bookId);
    }

    private String normalizeSegment(String value) {
        if (value == null || value.isBlank()) {
            return "anaquel";
        }

        return value
                .trim()
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
    }
}