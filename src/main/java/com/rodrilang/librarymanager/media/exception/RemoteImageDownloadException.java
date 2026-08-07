package com.rodrilang.librarymanager.media.exception;

public class RemoteImageDownloadException extends RuntimeException {

    private final boolean retryable;

    public RemoteImageDownloadException(
            String message,
            boolean retryable
    ) {
        super(message);
        this.retryable = retryable;
    }

    public RemoteImageDownloadException(
            String message,
            boolean retryable,
            Throwable cause
    ) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}