package com.rodrilang.librarymanager.integrations.tiendanube.exception;

import lombok.Getter;

import java.time.Duration;

@Getter
public class TiendanubeApiException extends RuntimeException {

    private final String operation;
    private final Integer httpStatus;
    private final String remoteErrorCode;
    private final TiendanubeApiErrorKind errorKind;
    private final Duration retryAfter;

    public TiendanubeApiException(String message) {
        this(message, null, null, null, null, TiendanubeApiErrorKind.UNKNOWN, null);
    }

    public TiendanubeApiException(String message, Throwable cause) {
        this(message, cause, null, null, null, TiendanubeApiErrorKind.UNKNOWN, null);
    }

    public TiendanubeApiException(String message, Throwable cause, String operation, Integer httpStatus,
                                  String remoteErrorCode, TiendanubeApiErrorKind errorKind, Duration retryAfter) {
        super(message, cause);
        this.operation = operation;
        this.httpStatus = httpStatus;
        this.remoteErrorCode = remoteErrorCode;
        this.errorKind = errorKind == null ? TiendanubeApiErrorKind.UNKNOWN : errorKind;
        this.retryAfter = retryAfter;
    }
}
