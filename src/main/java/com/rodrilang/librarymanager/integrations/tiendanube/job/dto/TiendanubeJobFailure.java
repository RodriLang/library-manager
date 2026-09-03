package com.rodrilang.librarymanager.integrations.tiendanube.job.dto;

import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobFailureDisposition;

public record TiendanubeJobFailure(
        String errorType,
        String message,
        Integer httpStatus,
        TiendanubeJobFailureDisposition disposition
) {
}
