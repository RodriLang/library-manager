package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListColumnMapping;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
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

    public boolean shouldSkip(PriceListRow row) {
        boolean hasIdentifier = hasText(row.isbn());
        boolean hasTitle = hasText(row.title());
        boolean hasAuthor = hasText(row.authorName());
        boolean hasPublisher = hasText(row.publisherName());
        boolean hasPrice = row.retailPrice() != null;

        if (!hasIdentifier && !hasTitle && !hasAuthor && !hasPublisher && !hasPrice) {
            return true;
        }

        return !hasIdentifier
                && hasTitle
                && !hasAuthor
                && !hasPublisher
                && !hasPrice;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}