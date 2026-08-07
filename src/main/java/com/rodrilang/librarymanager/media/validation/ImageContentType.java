package com.rodrilang.librarymanager.media.validation;

import java.util.Locale;
import java.util.Optional;

public enum ImageContentType {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String mimeType;
    private final String extension;

    ImageContentType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getExtension() {
        return extension;
    }

    public static Optional<ImageContentType> fromMimeType(
            String mimeType
    ) {
        if (mimeType == null || mimeType.isBlank()) {
            return Optional.empty();
        }

        String normalized = mimeType
                .trim()
                .toLowerCase(Locale.ROOT);

        for (ImageContentType type : values()) {
            if (type.mimeType.equals(normalized)) {
                return Optional.of(type);
            }
        }

        return Optional.empty();
    }
}