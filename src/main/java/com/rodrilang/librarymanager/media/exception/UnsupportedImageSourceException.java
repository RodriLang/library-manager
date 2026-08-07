package com.rodrilang.librarymanager.media.exception;

public class UnsupportedImageSourceException
        extends RemoteImageDownloadException {

    public UnsupportedImageSourceException(String sourceUrl) {
        super(
                "No se admite el origen de imagen: " + sourceUrl,
                false
        );
    }
}