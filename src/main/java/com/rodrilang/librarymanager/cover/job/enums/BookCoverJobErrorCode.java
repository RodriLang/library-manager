package com.rodrilang.librarymanager.cover.job.enums;

public enum BookCoverJobErrorCode {

    INVALID_SOURCE_URL(false),
    UNSUPPORTED_SOURCE(false),
    DRIVE_FILE_NOT_PUBLIC(false),
    REMOTE_FILE_NOT_FOUND(false),
    REMOTE_CONTENT_NOT_IMAGE(false),
    IMAGE_TOO_LARGE(false),
    INVALID_IMAGE(false),
    BOOK_NOT_FOUND(false),

    DOWNLOAD_TIMEOUT(true),
    REMOTE_SERVER_ERROR(true),
    RATE_LIMITED(true),
    STORAGE_ERROR(true),
    INTERNAL_ERROR(true);

    private final boolean retryable;

    BookCoverJobErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}