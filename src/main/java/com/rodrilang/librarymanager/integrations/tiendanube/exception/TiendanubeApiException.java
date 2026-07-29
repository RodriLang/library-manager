package com.rodrilang.librarymanager.integrations.tiendanube.exception;

public class TiendanubeApiException extends RuntimeException {

    public TiendanubeApiException(String message) {
        super(message);
    }

    public TiendanubeApiException(String message, Throwable cause) {
        super(message, cause);
    }
}