package com.rodrilang.librarymanager.dto.error;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp,
        Map<String, String> validationErrors,
        String action,
        String isbn,
        String nextEndpoint
) {

    public ErrorResponse(
            int status,
            String error,
            String message,
            String path,
            Instant timestamp,
            Map<String, String> validationErrors
    ) {
        this(
                status,
                error,
                message,
                path,
                timestamp,
                validationErrors,
                null,
                null,
                null
        );
    }
}