package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeApiErrorKind;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeApiException;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobFailure;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobFailureDisposition;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TiendanubeJobFailureFactoryTest {

    private final TiendanubeJobFailureFactory factory = new TiendanubeJobFailureFactory();

    @Test
    void rateLimitIsRetryableAndPreservesRetryAfter() {
        TiendanubeApiException exception = new TiendanubeApiException(
                "Too many requests", null, "actualizar stock", 429, "429",
                TiendanubeApiErrorKind.RATE_LIMIT, Duration.ofMillis(600)
        );

        TiendanubeJobFailure failure = factory.from(exception);

        assertEquals(TiendanubeJobFailureDisposition.RETRY, failure.disposition());
        assertEquals(429, failure.httpStatus());
        assertEquals(Duration.ofMillis(600), failure.retryAfter());
        assertEquals("RATE_LIMIT:429", failure.errorType());
    }

    @Test
    void unauthorizedErrorBlocksJob() {
        TiendanubeApiException exception = new TiendanubeApiException(
                "Unauthorized", null, "actualizar stock", 401, "INVALID_ACCESS_TOKEN",
                TiendanubeApiErrorKind.AUTHENTICATION, null
        );

        TiendanubeJobFailure failure = factory.from(exception);

        assertEquals(TiendanubeJobFailureDisposition.BLOCK, failure.disposition());
    }

    @Test
    void suspendedApiAccessBlocksJob() {
        TiendanubeApiException exception = new TiendanubeApiException(
                "Payment required", null, "actualizar stock", 402, "HTTP_402",
                TiendanubeApiErrorKind.ACCESS_SUSPENDED, null
        );

        TiendanubeJobFailure failure = factory.from(exception);

        assertEquals(TiendanubeJobFailureDisposition.BLOCK, failure.disposition());
    }

    @Test
    void validationErrorDoesNotRetry() {
        TiendanubeApiException exception = new TiendanubeApiException(
                "Validation error", null, "actualizar variante", 422, "422",
                TiendanubeApiErrorKind.CLIENT_ERROR, null
        );

        TiendanubeJobFailure failure = factory.from(exception);

        assertEquals(TiendanubeJobFailureDisposition.FAIL, failure.disposition());
    }

    @Test
    void businessErrorDoesNotRetry() {
        TiendanubeJobFailure failure = factory.from(new BusinessException("Estado inválido"));

        assertEquals(TiendanubeJobFailureDisposition.FAIL, failure.disposition());
    }

    @Test
    void unknownProgrammingErrorDoesNotRetryAutomatically() {
        TiendanubeJobFailure failure = factory.from(new NullPointerException("unexpected"));

        assertEquals(TiendanubeJobFailureDisposition.FAIL, failure.disposition());
    }
}
