package com.rodrilang.librarymanager.importer.price.validator;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

import static com.rodrilang.librarymanager.importer.price.util.PriceListNormalizationUtils.normalizeIsbn;
import static org.springframework.util.StringUtils.hasText;

@Component
@RequiredArgsConstructor
public class PriceListImportSafetyValidator {

    private static final BigDecimal MAX_REASONABLE_PRICE = new BigDecimal("500000");
    private static final double MIN_VALID_ROWS_RATIO = 0.80;
    private static final double MIN_PRICE_RATIO = 0.90;
    private static final double MIN_TITLE_RATIO = 0.90;
    private static final double MIN_ISBN_RATIO = 0.80;
    private static final int MIN_ROWS_WITH_ISBN_TO_VALIDATE_RATIO = 20;
    private static final double MAX_ABSURD_PRICE_RATIO = 0.10;

    public void validate(
            List<PriceListRow> rows,
            List<PriceListRow> validRows
    ) {
        if (rows.isEmpty()) {
            throw new BusinessException("El archivo no contiene filas para importar.");
        }

        if (validRows.isEmpty()) {
            throw new BusinessException("No se encontraron filas válidas para importar.");
        }

        double validRowsRatio = (double) validRows.size() / rows.size();

        if (validRowsRatio < MIN_VALID_ROWS_RATIO) {
            throw new BusinessException(
                    "El archivo tiene demasiadas filas inválidas. Verifique que corresponda al proveedor seleccionado."
            );
        }

        long rowsWithValidPrice = rows.stream()
                .filter(row -> row.retailPrice() != null)
                .count();

        double priceRatio = (double) rowsWithValidPrice / rows.size();

        if (priceRatio < MIN_PRICE_RATIO) {
            throw new BusinessException(
                    "La columna de precios no parece válida para el proveedor seleccionado."
            );
        }

        long rowsWithTitle = rows.stream()
                .filter(row -> hasText(row.title()))
                .count();

        double titleRatio = (double) rowsWithTitle / rows.size();

        if (titleRatio < MIN_TITLE_RATIO) {
            throw new BusinessException(
                    "La columna de títulos no parece válida para el proveedor seleccionado."
            );
        }

        long rowsWithIsbn = rows.stream()
                .filter(row -> hasText(row.isbn()))
                .count();

        long rowsWithValidIsbn = rows.stream()
                .filter(row -> normalizeIsbn(row.isbn()) != null)
                .count();

        if (rowsWithIsbn >= MIN_ROWS_WITH_ISBN_TO_VALIDATE_RATIO) {
            double isbnRatio = (double) rowsWithValidIsbn / rowsWithIsbn;

            if (isbnRatio < MIN_ISBN_RATIO) {
                throw new BusinessException(
                        "La columna ISBN no parece válida para el proveedor seleccionado."
                );
            }
        }

        long absurdPrices = rows.stream()
                .filter(row -> row.retailPrice() != null)
                .filter(row -> row.retailPrice().compareTo(MAX_REASONABLE_PRICE) > 0)
                .count();

        double absurdPriceRatio = (double) absurdPrices / rows.size();

        if (absurdPriceRatio > MAX_ABSURD_PRICE_RATIO) {
            throw new BusinessException(
                    "Se detectó una cantidad inusual de precios fuera de rango. No se realizó la importación."
            );
        }

    }
}