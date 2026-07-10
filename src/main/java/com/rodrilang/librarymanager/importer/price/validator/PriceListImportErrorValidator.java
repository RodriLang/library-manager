package com.rodrilang.librarymanager.importer.price.validator;

import com.rodrilang.librarymanager.enums.RowValidationSeverity;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportError;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PriceListImportErrorValidator {

    public void validate(List<PriceListImportError> errors) {
        boolean hasErrors = errors.stream()
                .anyMatch(error -> error.severity() == RowValidationSeverity.ERROR);

        if (hasErrors) {
            throw new BusinessException(
                    "El archivo contiene filas inválidas. Corrija los errores antes de importar."
            );
        }
    }
}