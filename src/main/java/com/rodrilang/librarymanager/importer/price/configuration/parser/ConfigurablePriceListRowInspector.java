package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListColumnMapping;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListMetadata;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
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
        return !hasText(row.isbn())
                && !hasText(row.title())
                && !hasText(row.authorName())
                && !hasText(row.publisherName())
                && !hasText(row.categoryName())
                && row.retailPrice() == null
                && !hasMetadata(row.metadata());
    }

    private boolean hasMetadata(
            PriceListMetadata metadata
    ) {
        if (metadata == null) {
            return false;
        }

        return hasText(metadata.externalCode())
                || hasText(metadata.subtitle())
                || hasText(metadata.description())
                || hasText(metadata.genreName())
                || hasText(metadata.collectionName())
                || hasText(metadata.language())
                || hasText(metadata.coverUrl())
                || hasText(metadata.tags())
                || hasText(metadata.observations())
                || metadata.externalStock() != null
                || metadata.pageCount() != null
                || metadata.publicationDate() != null
                || metadata.widthCm() != null
                || metadata.heightCm() != null
                || metadata.depthCm() != null
                || metadata.weightGrams() != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}