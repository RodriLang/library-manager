package com.rodrilang.librarymanager.importer.price.validator;

import com.rodrilang.librarymanager.importer.price.dto.PriceListImportError;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.hasText;
import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.normalizeIsbn;

@Component
public class PriceListRowValidator {

    public Optional<PriceListImportError> validateRow(
            PriceListRow row,
            Set<String> repeatedIsbns
    ) {
        Optional<PriceListImportError> validationError = validateRequiredFields(row);

        if (validationError.isPresent()) {
            return validationError;
        }

        String isbn = normalizeIsbn(row.isbn());

        if (isbn != null && !repeatedIsbns.add(isbn)) {
            return Optional.of(new PriceListImportError(
                    row.rowNumber(),
                    row.isbn(),
                    "ISBN repetido dentro del archivo."
            ));
        }

        return Optional.empty();
    }

    private Optional<PriceListImportError> validateRequiredFields(PriceListRow row) {

        if (!hasText(row.title())) {
            return Optional.of(new PriceListImportError(
                    row.rowNumber(),
                    row.isbn(),
                    "Fila inválida: título faltante."
            ));
        }

        if (row.retailPrice() == null) {
            return Optional.of(new PriceListImportError(
                    row.rowNumber(),
                    row.isbn(),
                    "Fila inválida: precio faltante."
            ));
        }

        return Optional.empty();
    }
}