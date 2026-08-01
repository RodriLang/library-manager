package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListField;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListColumnMapping;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.dto.PriceListMetadata;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ConfigurablePriceListRowMapper {

    private final PriceListCellValueConverter converter;

    public PriceListRow map(
            Row row,
            PriceListImportConfig config
    ) {
        Map<PriceListField, Object> values =
                new EnumMap<>(PriceListField.class);

        for (PriceListColumnMapping mapping
                : config.getMappings()) {

            if (!mapping.isActive()) {
                continue;
            }

            Cell cell = row.getCell(
                    mapping.getColumnIndex(),
                    Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
            );

            Object value = converter.convert(
                    cell,
                    mapping.getValueType()
            );

            values.put(
                    mapping.getTargetField(),
                    value
            );
        }

        return buildRow(
                row.getRowNum(),
                values
        );
    }

    private PriceListRow buildRow(
            int rowIndex,
            Map<PriceListField, Object> values
    ) {
        PriceListMetadata metadata =
                PriceListMetadata.builder()

                        .subtitle(
                                text(values, PriceListField.SUBTITLE)
                        )

                        .description(
                                text(values, PriceListField.DESCRIPTION)
                        )

                        .genreName(
                                text(values, PriceListField.GENRE)
                        )

                        .pageCount(
                                integer(values, PriceListField.PAGE_COUNT)
                        )

                        .publicationDate(
                                date(
                                        values,
                                        PriceListField.PUBLICATION_DATE
                                )
                        )

                        .language(
                                text(values, PriceListField.LANGUAGE)
                        )

                        .coverUrl(
                                text(values, PriceListField.COVER_URL)
                        )

                        .collectionName(
                                text(values, PriceListField.COLLECTION)
                        )

                        .dimensions(
                                text(values, PriceListField.DIMENSIONS)
                        )

                        .weight(
                                decimal(values, PriceListField.WEIGHT)
                        )

                        .tags(
                                text(values, PriceListField.TAGS)
                        )

                        .externalCode(
                                text(
                                        values,
                                        PriceListField.EXTERNAL_CODE
                                )
                        )

                        .externalStock(
                                integer(
                                        values,
                                        PriceListField.EXTERNAL_STOCK
                                )
                        )

                        .observations(
                                text(
                                        values,
                                        PriceListField.OBSERVATIONS
                                )
                        )

                        .build();

        return new PriceListRow(
                rowIndex + 1,

                text(values, PriceListField.ISBN),

                text(values, PriceListField.TITLE),

                text(values, PriceListField.AUTHOR),

                text(values, PriceListField.PUBLISHER),

                decimal(
                        values,
                        PriceListField.RETAIL_PRICE
                ),

                /*
                 * Temporal.
                 *
                 * El configurable todavía no utiliza
                 * PriceListSource.
                 */
                null,

                text(values, PriceListField.CATEGORY),

                BookSource.EXTERNAL_METADATA,

                metadata
        );
    }

    private String text(
            Map<PriceListField, Object> values,
            PriceListField field
    ) {
        Object value = values.get(field);

        return value != null
                ? value.toString().trim()
                : null;
    }

    private BigDecimal decimal(
            Map<PriceListField, Object> values,
            PriceListField field
    ) {
        return (BigDecimal) values.get(field);
    }

    private Integer integer(
            Map<PriceListField, Object> values,
            PriceListField field
    ) {
        return (Integer) values.get(field);
    }

    private LocalDate date(
            Map<PriceListField, Object> values,
            PriceListField field
    ) {
        return (LocalDate) values.get(field);
    }
}