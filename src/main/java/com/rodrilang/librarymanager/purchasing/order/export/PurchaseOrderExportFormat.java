package com.rodrilang.librarymanager.purchasing.order.export;

import com.rodrilang.librarymanager.exception.BusinessException;

import java.util.Locale;

public enum PurchaseOrderExportFormat {

    PDF,
    XLSX,
    CSV;

    public static PurchaseOrderExportFormat from(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Debe indicar un formato de descarga.");
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("Formato de descarga no válido. Use PDF, XLSX o CSV.");
        }
    }
}
