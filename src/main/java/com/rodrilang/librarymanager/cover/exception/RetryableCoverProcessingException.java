package com.rodrilang.librarymanager.cover.exception;

public class RetryableCoverProcessingException
        extends RuntimeException {

    public RetryableCoverProcessingException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }

    public RetryableCoverProcessingException(String message) {
        super(message);
    }
}