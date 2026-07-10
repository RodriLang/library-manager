package com.rodrilang.librarymanager.importer.price.validator;

import com.rodrilang.librarymanager.enums.RowValidationSeverity;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportError;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.dto.PriceListValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.normalizeIsbn;

@Component
@RequiredArgsConstructor
public class PriceListValidationService {

    private final PriceListRowValidator rowValidator;

    public PriceListValidationResult validate(List<PriceListRow> rows) {
        List<PriceListRow> validRows = new ArrayList<>();
        List<PriceListImportError> errors = new ArrayList<>();
        Map<String, PriceListRow> firstRowsByIsbn = new LinkedHashMap<>();

        for (PriceListRow row : rows) {
            List<PriceListImportError> rowErrors = rowValidator.validateRow(row);

            errors.addAll(rowErrors);

            boolean hasError = rowErrors.stream()
                    .anyMatch(error -> error.severity() == RowValidationSeverity.ERROR);

            if (!hasError && !isDuplicateRow(row, firstRowsByIsbn, errors)) {
                validRows.add(row);
            }
        }

        return new PriceListValidationResult(validRows, errors);
    }

    private boolean sameBookData(PriceListRow first, PriceListRow second) {
        return normalize(first.title()).equals(normalize(second.title()))
                && normalize(first.authorName()).equals(normalize(second.authorName()))
                && normalize(first.publisherName()).equals(normalize(second.publisherName()))
                && pricesEqual(first, second);
    }

    private boolean pricesEqual(PriceListRow first, PriceListRow second) {
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

    private boolean isDuplicateRow(
            PriceListRow row,
            Map<String, PriceListRow> firstRowsByIsbn,
            List<PriceListImportError> errors
    ) {
        String isbn = normalizeIsbn(row.isbn());

        if (isbn == null) {
            return false;
        }

        PriceListRow firstRow = firstRowsByIsbn.get(isbn);

        if (firstRow == null) {
            firstRowsByIsbn.put(isbn, row);
            return false;
        }

        if (!sameBookData(firstRow, row)) {
            errors.add(new PriceListImportError(
                    row.rowNumber(),
                    row.isbn(),
                    String.format(
                            "ISBN repetido con datos diferentes. Se conserva la primera aparición en la fila %d y se omite esta fila.",
                            firstRow.rowNumber()
                    ),
                    RowValidationSeverity.WARNING
            ));
        }

        return true;
    }
}