package com.rodrilang.librarymanager.integrations.tiendanube.job.dto;

import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobFailureDisposition;

import java.time.Duration;

public record TiendanubeJobFailure(
        String errorType,
        String message,
        Integer httpStatus,
        TiendanubeJobFailureDisposition disposition,
        Duration retryAfter
) {
}
