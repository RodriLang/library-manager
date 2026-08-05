package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;

@Component
public class PriceListSheetResolver {

    public Sheet resolve(
            Workbook workbook,
            PriceListImportConfig config
    ) {
        return switch (config.getSheetStrategy()) {

            case BY_INDEX -> resolveByIndex(
                    workbook,
                    config.getSheetIndex()
            );

            case BY_NAME -> resolveByName(
                    workbook,
                    config.getSheetName()
            );

            case NAME_CONTAINS -> resolveByNameContains(
                    workbook,
                    config.getSheetName()
            );
        };
    }

    private Sheet resolveByIndex(
            Workbook workbook,
            Integer index
    ) {
        if (index == null
                || index < 0
                || index >= workbook.getNumberOfSheets()) {

            throw new BusinessException(
                    "No se encontró la hoja configurada en el archivo."
            );
        }

        return workbook.getSheetAt(index);
    }

    private Sheet resolveByName(
            Workbook workbook,
            String name
    ) {
        Sheet sheet = workbook.getSheet(name);

        if (sheet == null) {
            throw new BusinessException(
                    "No se encontró la hoja '" + name + "'."
            );
        }

        return sheet;
    }

    private Sheet resolveByNameContains(
            Workbook workbook,
            String expected
    ) {
        String normalizedExpected =
                expected.trim().toLowerCase();

        for (int i = 0;
             i < workbook.getNumberOfSheets();
             i++) {

            Sheet sheet = workbook.getSheetAt(i);

            if (sheet.getSheetName()
                    .toLowerCase()
                    .contains(normalizedExpected)) {

                return sheet;
            }
        }

        throw new BusinessException(
                "No se encontró una hoja cuyo nombre contenga '"
                        + expected
                        + "'."
        );
    }
}