package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListField;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListColumnMapping;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListMetadata;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ConfigurableStreamingRowMapper {

    private final PriceListCellValueConverter converter;

    public PriceListRow map(
            int rowIndex,
            List<String> cells,
            PriceListImportConfig config
    ) {
        Map<PriceListField, Object> values =
                new EnumMap<>(PriceListField.class);

        for (PriceListColumnMapping mapping : config.getMappings()) {
            if (!mapping.isActive()) {
                continue;
            }

            String rawValue = getValue(
                    cells,
                    mapping.getColumnIndex()
            );

            Object value = converter.convert(
                    rawValue,
                    mapping.getValueType()
            );

            values.put(
                    mapping.getTargetField(),
                    value
            );
        }

        return buildRow(rowIndex, values);
    }

    private PriceListRow buildRow(
            int rowIndex,
            Map<PriceListField, Object> values
    ) {
        ParsedDimensions dimensions = parseDimensions(
                text(values, PriceListField.DIMENSIONS)
        );

        BigDecimal weightGrams = parseWeightGrams(
                decimal(values, PriceListField.WEIGHT)
        );

        PriceListMetadata metadata = PriceListMetadata.builder()
                .subtitle(text(values, PriceListField.SUBTITLE))
                .description(text(values, PriceListField.DESCRIPTION))
                .genreName(text(values, PriceListField.GENRE))
                .pageCount(integer(values, PriceListField.PAGE_COUNT))
                .publicationDate(
                        date(values, PriceListField.PUBLICATION_DATE)
                )
                .language(text(values, PriceListField.LANGUAGE))
                .sourceCoverUrl(text(values, PriceListField.COVER_URL))
                .collectionName(text(values, PriceListField.COLLECTION))
                .widthCm(dimensions.widthCm())
                .heightCm(dimensions.heightCm())
                .depthCm(dimensions.depthCm())
                .weightGrams(weightGrams)
                .tags(text(values, PriceListField.TAGS))
                .externalCode(
                        text(values, PriceListField.EXTERNAL_CODE)
                )
                .externalStock(
                        integer(values, PriceListField.EXTERNAL_STOCK)
                )
                .observations(
                        text(values, PriceListField.OBSERVATIONS)
                )
                .build();

        return new PriceListRow(
                rowIndex + 1,
                text(values, PriceListField.ISBN),
                text(values, PriceListField.TITLE),
                text(values, PriceListField.AUTHOR),
                text(values, PriceListField.PUBLISHER),
                decimal(values, PriceListField.RETAIL_PRICE),
                text(values, PriceListField.CATEGORY),
                BookSource.EXTERNAL_METADATA,
                metadata
        );
    }

    private String getValue(
            List<String> cells,
            int columnIndex
    ) {
        if (columnIndex < 0 || columnIndex >= cells.size()) {
            return "";
        }

        return cells.get(columnIndex);
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

    private ParsedDimensions parseDimensions(String value) {
        if (value == null || value.isBlank()) {
            return ParsedDimensions.empty();
        }

        String normalized = value.trim()
                .toLowerCase()
                .replace("cms", "")
                .replace("cm", "")
                .replace("×", "x");

        String[] parts = normalized.split("\\s*x\\s*");

        if (parts.length < 2 || parts.length > 3) {
            return ParsedDimensions.empty();
        }

        try {
            BigDecimal widthCm =
                    parseDimensionValue(parts[0]);

            BigDecimal heightCm =
                    parseDimensionValue(parts[1]);

            BigDecimal depthCm =
                    parts.length == 3
                            ? parseDimensionValue(parts[2])
                            : null;

            return new ParsedDimensions(
                    widthCm,
                    heightCm,
                    depthCm
            );
        } catch (NumberFormatException exception) {
            return ParsedDimensions.empty();
        }
    }

    private BigDecimal parseDimensionValue(String value) {
        return new BigDecimal(
                value.trim().replace(",", ".")
        );
    }

    private BigDecimal parseWeightGrams(
            BigDecimal weightKilograms
    ) {
        if (weightKilograms == null) {
            return null;
        }

        return weightKilograms.multiply(
                BigDecimal.valueOf(1000)
        );
    }

    private record ParsedDimensions(
            BigDecimal widthCm,
            BigDecimal heightCm,
            BigDecimal depthCm
    ) {

        private static ParsedDimensions empty() {
            return new ParsedDimensions(
                    null,
                    null,
                    null
            );
        }
    }
}