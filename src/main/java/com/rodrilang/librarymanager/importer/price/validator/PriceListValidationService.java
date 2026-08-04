package com.rodrilang.librarymanager.importer.price.validator;

import com.rodrilang.librarymanager.enums.RowValidationSeverity;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportError;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.dto.PriceListValidationResult;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PriceListValidationService {

    private final PriceListRowValidator rowValidator;
    private final IsbnService isbnService;

    public PriceListValidationResult validate(List<PriceListRow> rows) {
        List<PriceListRow> validRows = new ArrayList<>();
        List<PriceListImportError> errors = new ArrayList<>();
        Map<String, PriceListRow> firstRowsByCanonicalIsbn = new LinkedHashMap<>();

        for (PriceListRow row : rows) {
            List<PriceListImportError> rowErrors = rowValidator.validateRow(row);

            errors.addAll(rowErrors);

            boolean hasError = rowErrors.stream()
                    .anyMatch(error -> error.severity() == RowValidationSeverity.ERROR);

            if (!hasError && !isDuplicateRow(
                    row,
                    firstRowsByCanonicalIsbn,
                    errors
            )) {
                validRows.add(row);
            }
        }

        return new PriceListValidationResult(validRows, errors);
    }

    private boolean isDuplicateRow(
            PriceListRow row,
            Map<String, PriceListRow> firstRowsByCanonicalIsbn,
            List<PriceListImportError> errors
    ) {
        ParsedIsbn parsedIsbn = isbnService.parse(row.isbn());

        if (!parsedIsbn.valid()) {
            return false;
        }

        String canonicalIsbn = parsedIsbn.isbn13();

        PriceListRow firstRow = firstRowsByCanonicalIsbn.get(canonicalIsbn);

        if (firstRow == null) {
            firstRowsByCanonicalIsbn.put(canonicalIsbn, row);
            return false;
        }

        if (!sameBookData(firstRow, row)) {
            errors.add(new PriceListImportError(
                    row.rowNumber(),
                    row.isbn(),
                    String.format(
                            "ISBN repetido con datos diferentes. "
                                    + "Se conserva la primera aparición en la fila %d "
                                    + "y se omite esta fila.",
                            firstRow.rowNumber()
                    ),
                    RowValidationSeverity.WARNING
            ));
        }

        return true;
    }

    private boolean sameBookData(
            PriceListRow first,
            PriceListRow second
    ) {
        return normalize(first.title()).equals(normalize(second.title()))
                && normalize(first.authorName()).equals(normalize(second.authorName()))
                && normalize(first.publisherName()).equals(normalize(second.publisherName()))
                && pricesEqual(first, second);
    }

    private boolean pricesEqual(
            PriceListRow first,
            PriceListRow second
    ) {
        if (first.retailPrice() == null && second.retailPrice() == null) {
            return true;
        }

        if (first.retailPrice() == null || second.retailPrice() == null) {
            return false;
        }

        return first.retailPrice().compareTo(second.retailPrice()) == 0;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toLowerCase();
    }
}