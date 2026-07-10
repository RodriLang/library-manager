package com.rodrilang.librarymanager.importer.price.validator;

import com.rodrilang.librarymanager.enums.RowValidationSeverity;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportError;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.hasText;
import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.normalizeIsbn;

@Component
public class PriceListRowValidator {

    public List<PriceListImportError> validateRow(PriceListRow row) {
        List<PriceListImportError> errors = new ArrayList<>();

        if (!hasText(row.title())) {
            errors.add(new PriceListImportError(
                    row.rowNumber(),
                    row.isbn(),
                    "Fila inválida: título faltante.",
                    RowValidationSeverity.ERROR
            ));
        }

        if (row.retailPrice() == null) {
            errors.add(new PriceListImportError(
                    row.rowNumber(),
                    row.isbn(),
                    "Fila inválida: precio faltante.",
                    RowValidationSeverity.ERROR
            ));
        }

        if (hasText(row.isbn()) && normalizeIsbn(row.isbn()) == null) {
            errors.add(new PriceListImportError(
                    row.rowNumber(),
                    row.isbn(),
                    "ISBN inválido. Se importará el libro sin ISBN.",
                    RowValidationSeverity.WARNING
            ));
        }

        return errors;
    }
}