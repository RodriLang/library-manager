package com.rodrilang.librarymanager.integrations.tiendanube.exception;

public class TiendanubeRemoteResourceNotFoundException extends TiendanubeApiException {

    public TiendanubeRemoteResourceNotFoundException(String message, Throwable cause) {
        this(null, message, null, cause);
    }

    public TiendanubeRemoteResourceNotFoundException(String operation, String message, String remoteErrorCode,
                                                     Throwable cause) {
        super(message, cause, operation, 404, remoteErrorCode, TiendanubeApiErrorKind.NOT_FOUND, null);
    }
}
