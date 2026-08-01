package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListValueType;
import com.rodrilang.librarymanager.importer.price.util.ExcelCellValueReader;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PriceListCellValueConverter {

    private final ExcelCellValueReader cellValueReader;

    public Object convert(
            Cell cell,
            PriceListValueType type
    ) {
        if (cell == null) {
            return null;
        }

        String raw = cellValueReader.read(cell);

        if (raw.isBlank()) {
            return null;
        }

        return switch (type) {

            case TEXT, URL ->
                    raw.trim();

            case ISBN ->
                    normalizeIsbn(raw);

            case DECIMAL ->
                    parseDecimal(cell, raw);

            case INTEGER ->
                    parseInteger(raw);

            case DATE ->
                    parseDate(cell, raw);
        };
    }

    private String normalizeIsbn(String value) {
        String normalized = value.replaceAll(
                "[^0-9Xx]",
                ""
        );

        return normalized.isBlank()
                ? null
                : normalized;
    }

    private BigDecimal parseDecimal(
            Cell cell,
            String raw
    ) {
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(
                    cell.getNumericCellValue()
            );
        }

        String normalized = raw
                .replace("$", "")
                .replaceAll("\\s+", "")
                .trim();

        /*
         * 14.500,00
         */
        if (normalized.matches(
                ".*\\.\\d{3},\\d+"
        )) {
            normalized = normalized
                    .replace(".", "")
                    .replace(",", ".");
        } else {
            normalized = normalized
                    .replace(",", ".");
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseInteger(String raw) {
        try {
            return new BigDecimal(
                    raw.replace(",", ".")
            ).intValue();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate parseDate(
            Cell cell,
            String raw
    ) {
        if (cell.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {

            return cell
                    .getLocalDateTimeCellValue()
                    .toLocalDate();
        }

        return parseTextDate(raw);
    }

    private LocalDate parseTextDate(String value) {
        List<DateTimeFormatter> formatters = List.of(

                DateTimeFormatter.ofPattern("yyyy/MM/dd"),

                new DateTimeFormatterBuilder()
                        .appendPattern("yyyy/MM")
                        .parseDefaulting(
                                ChronoField.DAY_OF_MONTH,
                                1
                        )
                        .toFormatter(),

                DateTimeFormatter.ofPattern("dd/MM/yyyy"),

                DateTimeFormatter.ISO_LOCAL_DATE
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(
                        value,
                        formatter
                );
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }
}