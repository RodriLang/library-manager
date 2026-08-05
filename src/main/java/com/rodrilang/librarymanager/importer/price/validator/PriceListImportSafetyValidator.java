package com.rodrilang.librarymanager.importer.price.validator;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListImportSafetySummary;
import org.springframework.stereotype.Component;

@Component
public class PriceListImportSafetyValidator {

    private static final double MIN_VALID_ROWS_RATIO = 0.80;
    private static final double MIN_PRICE_RATIO = 0.90;
    private static final double MIN_TITLE_RATIO = 0.90;
    private static final double MIN_ISBN_RATIO = 0.80;

    private static final int MIN_ROWS_WITH_ISBN_TO_VALIDATE_RATIO = 20;
    private static final double MAX_ABSURD_PRICE_RATIO = 0.10;

    public void validate(
            PriceListImportSafetySummary summary
    ) {
        long processableRows = summary.processableRows();

        if (processableRows <= 0) {
            throw new BusinessException(
                    "El archivo no contiene filas para importar."
            );
        }

        long acceptedRows =
                summary.validRows()
                        + summary.duplicateRows();

        if (acceptedRows <= 0) {
            throw new BusinessException(
                    "No se encontraron filas válidas para importar."
            );
        }

        double validRowsRatio =
                ratio(acceptedRows, processableRows);

        if (validRowsRatio < MIN_VALID_ROWS_RATIO) {
            throw new BusinessException(
                    "El archivo tiene demasiadas filas inválidas. "
                            + "Filas procesables: " + processableRows
                            + ", válidas: " + summary.validRows()
                            + ", duplicadas: " + summary.duplicateRows()
                            + ", inválidas: " + summary.invalidRows()
                            + ". Verifique la configuración del proveedor."
            );
        }

        double priceRatio =
                ratio(
                        summary.rowsWithPrice(),
                        processableRows
                );

        if (priceRatio < MIN_PRICE_RATIO) {
            throw new BusinessException(
                    "La columna de precios no parece válida para el proveedor seleccionado."
            );
        }

        double titleRatio =
                ratio(
                        summary.rowsWithTitle(),
                        processableRows
                );

        if (titleRatio < MIN_TITLE_RATIO) {
            throw new BusinessException(
                    "La columna de títulos no parece válida para el proveedor seleccionado."
            );
        }

        if (
                summary.rowsWithIsbn()
                        >= MIN_ROWS_WITH_ISBN_TO_VALIDATE_RATIO
        ) {
            double isbnRatio =
                    ratio(
                            summary.rowsWithValidIsbn(),
                            summary.rowsWithIsbn()
                    );

            if (isbnRatio < MIN_ISBN_RATIO) {
                throw new BusinessException(
                        "La columna ISBN no parece válida para el proveedor seleccionado."
                );
            }
        }

        double absurdPriceRatio =
                ratio(
                        summary.rowsWithAbsurdPrice(),
                        processableRows
                );

        if (absurdPriceRatio > MAX_ABSURD_PRICE_RATIO) {
            throw new BusinessException(
                    "Se detectó una cantidad inusual de precios fuera de rango. "
                            + "No se realizó la importación."
            );
        }
    }

    private double ratio(
            long numerator,
            long denominator
    ) {
        if (denominator <= 0) {
            return 0;
        }

        return (double) numerator / denominator;
    }
}