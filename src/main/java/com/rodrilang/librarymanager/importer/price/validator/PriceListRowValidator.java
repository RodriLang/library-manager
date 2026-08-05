package com.rodrilang.librarymanager.importer.price.validator;

import com.rodrilang.librarymanager.enums.RowValidationSeverity;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListIdentifier;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportError;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.enums.PriceListIdentifierType;
import com.rodrilang.librarymanager.importer.price.resolver.PriceListIdentifierResolver;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.hasText;

@Component
@RequiredArgsConstructor
public class PriceListRowValidator {

    private final IsbnService isbnService;
    private final PriceListIdentifierResolver identifierResolver;

    public List<PriceListImportError> validateRow(
            PriceListRow row
    ) {
        List<PriceListImportError> errors = new ArrayList<>();

        if (!hasText(row.title())) {
            errors.add(new PriceListImportError(
                    row.rowNumber(),
                    row.isbn(),
                    "Fila omitida: título faltante.",
                    RowValidationSeverity.ERROR
            ));
        }

        if (row.retailPrice() == null) {
            errors.add(new PriceListImportError(
                    row.rowNumber(),
                    row.isbn(),
                    "Fila omitida: el proveedor no informa un precio.",
                    RowValidationSeverity.ERROR
            ));
        }

        if (!hasText(row.isbn())) {
            return errors;
        }

        ParsedIsbn reported = isbnService.parse(row.isbn());

        PriceListIdentifier identifier =
                identifierResolver.resolve(row);

        if (reported.recovered()) {
            errors.add(new PriceListImportError(
                    row.rowNumber(),
                    row.isbn(),
                    "ISBN corregido automáticamente a "
                            + reported.isbn13()
                            + ".",
                    RowValidationSeverity.WARNING
            ));

            return errors;
        }

        if (!reported.valid()
                && identifier.type() == PriceListIdentifierType.ISBN) {

            errors.add(new PriceListImportError(
                    row.rowNumber(),
                    row.isbn(),
                    "El ISBN informado es inválido. "
                            + "Se utilizó el ISBN válido de la columna código: "
                            + identifier.isbn13()
                            + ".",
                    RowValidationSeverity.WARNING
            ));

            return errors;
        }

        if (!reported.valid()) {
            errors.add(new PriceListImportError(
                    row.rowNumber(),
                    row.isbn(),
                    "El identificador no es un ISBN válido. "
                            + "Se conservará como identificador del proveedor.",
                    RowValidationSeverity.WARNING
            ));
        }

        return errors;
    }
}