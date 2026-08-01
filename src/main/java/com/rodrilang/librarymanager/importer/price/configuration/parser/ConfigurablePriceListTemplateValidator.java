package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.enums.HeaderStrategy;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListColumnMapping;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.util.ExcelCellValueReader;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConfigurablePriceListTemplateValidator {

    private final ExcelCellValueReader cellValueReader;

    public void validate(
            Sheet sheet,
            PriceListImportConfig config
    ) {
        if (config.getHeaderStrategy()
                == HeaderStrategy.NONE) {
            return;
        }

        Row headerRow = sheet.getRow(
                config.getHeaderRowIndex()
        );

        if (headerRow == null) {
            throw new BusinessException(
                    "No se encontró la fila de encabezado configurada."
            );
        }

        for (PriceListColumnMapping mapping
                : config.getMappings()) {

            if (!mapping.isActive()) {
                continue;
            }

            validateMappingHeader(
                    headerRow,
                    mapping
            );
        }
    }

    private void validateMappingHeader(
            Row headerRow,
            PriceListColumnMapping mapping
    ) {
        String expected = mapping.getExpectedHeader();

        if (expected == null || expected.isBlank()) {
            return;
        }

        String actual = cellValueReader.read(
                headerRow.getCell(
                        mapping.getColumnIndex(),
                        Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                )
        );

        if (!normalize(expected).equals(normalize(actual))) {
            throw new BusinessException(
                    "El formato de la lista cambió. "
                            + "Se esperaba el encabezado '"
                            + expected
                            + "' en la columna "
                            + (mapping.getColumnIndex() + 1)
                            + ", pero se encontró '"
                            + actual
                            + "'."
            );
        }
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase();
    }
}