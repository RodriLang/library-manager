package com.rodrilang.librarymanager.integrations.tiendanube.job.exception;

import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobFailureDisposition;
import lombok.Getter;

@Getter
public class TiendanubeJobExecutionException extends RuntimeException {

    private final String errorType;
    private final Integer httpStatus;
    private final TiendanubeJobFailureDisposition disposition;

    public TiendanubeJobExecutionException(String errorType, String message, Integer httpStatus,
                                           TiendanubeJobFailureDisposition disposition, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.httpStatus = httpStatus;
        this.disposition = disposition;
    }

    public static TiendanubeJobExecutionException retryable(String errorType, String message, Throwable cause) {
        return new TiendanubeJobExecutionException(errorType, message, null, TiendanubeJobFailureDisposition.RETRY, cause);
    }

    public static TiendanubeJobExecutionException nonRetryable(String errorType, String message, Throwable cause) {
        return new TiendanubeJobExecutionException(errorType, message, null, TiendanubeJobFailureDisposition.FAIL, cause);
    }

    public static TiendanubeJobExecutionException blocked(String errorType, String message, Integer httpStatus, Throwable cause) {
        return new TiendanubeJobExecutionException(errorType, message, httpStatus, TiendanubeJobFailureDisposition.BLOCK, cause);
    }
}
