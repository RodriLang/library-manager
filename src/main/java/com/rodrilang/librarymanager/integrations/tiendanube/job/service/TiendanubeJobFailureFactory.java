package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

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
                    exception.getDisposition()
            );
        }

        return new TiendanubeJobFailure(
                throwable.getClass().getSimpleName(),
                resolveMessage(throwable),
                null,
                TiendanubeJobFailureDisposition.RETRY
        );
    }

    private String resolveMessage(Throwable throwable) {
        if (throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return throwable.getMessage();
    }
}
