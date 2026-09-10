package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeApiErrorKind;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeApiException;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobFailure;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobFailureDisposition;
import com.rodrilang.librarymanager.integrations.tiendanube.job.exception.TiendanubeJobExecutionException;
import org.springframework.stereotype.Component;

@Component
public class TiendanubeJobFailureFactory {

    public TiendanubeJobFailure from(Throwable throwable) {
        if (throwable instanceof TiendanubeJobExecutionException exception) {
            return new TiendanubeJobFailure(
                    exception.getErrorType(),
                    resolveMessage(exception),
                    exception.getHttpStatus(),
                    exception.getDisposition(),
                    null
            );
        }

        if (throwable instanceof TiendanubeApiException exception) {
            return new TiendanubeJobFailure(
                    resolveApiErrorType(exception),
                    resolveMessage(exception),
                    exception.getHttpStatus(),
                    disposition(exception.getErrorKind()),
                    exception.getRetryAfter()
            );
        }

        if (throwable instanceof BusinessException
                || throwable instanceof ResourceNotFoundException
                || throwable instanceof IllegalArgumentException
                || throwable instanceof IllegalStateException) {
            return new TiendanubeJobFailure(
                    throwable.getClass().getSimpleName(),
                    resolveMessage(throwable),
                    null,
                    TiendanubeJobFailureDisposition.FAIL,
                    null
            );
        }

        return new TiendanubeJobFailure(
                throwable.getClass().getSimpleName(),
                resolveMessage(throwable),
                null,
                TiendanubeJobFailureDisposition.FAIL,
                null
        );
    }

    private TiendanubeJobFailureDisposition disposition(TiendanubeApiErrorKind kind) {
        return switch (kind) {
            case AUTHENTICATION, AUTHORIZATION, ACCESS_SUSPENDED -> TiendanubeJobFailureDisposition.BLOCK;
            case NOT_FOUND, CLIENT_ERROR -> TiendanubeJobFailureDisposition.FAIL;
            case RATE_LIMIT, SERVER_ERROR, TIMEOUT, NETWORK -> TiendanubeJobFailureDisposition.RETRY;
            case UNKNOWN -> TiendanubeJobFailureDisposition.FAIL;
        };
    }

    private String resolveApiErrorType(TiendanubeApiException exception) {
        if (exception.getRemoteErrorCode() == null || exception.getRemoteErrorCode().isBlank()) {
            return exception.getErrorKind().name();
        }

        return exception.getErrorKind().name() + ":" + exception.getRemoteErrorCode();
    }

    private String resolveMessage(Throwable throwable) {
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return throwable.getMessage();
    }
}
