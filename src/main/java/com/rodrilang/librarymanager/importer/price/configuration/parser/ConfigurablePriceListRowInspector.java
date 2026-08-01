package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListColumnMapping;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.util.ExcelCellValueReader;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConfigurablePriceListRowInspector {

    private final ExcelCellValueReader cellValueReader;

    public boolean isBlank(
            Row row,
            PriceListImportConfig config
    ) {
        if (row == null) {
            return true;
        }

        return config.getMappings()
                .stream()
                .filter(PriceListColumnMapping::isActive)
                .allMatch(mapping -> {

                    Cell cell = row.getCell(
                            mapping.getColumnIndex(),
                            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                    );

                    return cellValueReader
                            .read(cell)
                            .isBlank();
                });
    }
}