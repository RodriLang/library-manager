package com.rodrilang.librarymanager.fiscal.exception;

public class ArcaApiException extends RuntimeException {

    public ArcaApiException(String message) {
        super(message);
    }

    public ArcaApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
