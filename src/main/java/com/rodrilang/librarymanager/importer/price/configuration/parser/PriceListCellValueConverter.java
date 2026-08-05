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
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PriceListCellValueConverter {

    private static DateTimeFormatter strictFormatter(String pattern) {
        return new DateTimeFormatterBuilder()
                .appendPattern(pattern)
                .toFormatter()
                .withResolverStyle(ResolverStyle.SMART);
    }

    private static final List<DateTimeFormatter> DATE_FORMATTERS =
            List.of(
                    DateTimeFormatter.ISO_LOCAL_DATE,

                    DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"),

                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                    DateTimeFormatter.ofPattern("d/M/yyyy"),
                    DateTimeFormatter.ofPattern("dd/M/yyyy"),
                    DateTimeFormatter.ofPattern("d/MM/yyyy"),

                    DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                    DateTimeFormatter.ofPattern("d-M-yyyy"),
                    DateTimeFormatter.ofPattern("dd-M-yyyy"),
                    DateTimeFormatter.ofPattern("d-MM-yyyy"),

                    DateTimeFormatter.ofPattern("d/M/yy"),
                    DateTimeFormatter.ofPattern("dd/MM/yy"),
                    DateTimeFormatter.ofPattern("d-M-yy"),
                    DateTimeFormatter.ofPattern("dd-MM-yy"),

                    new DateTimeFormatterBuilder()
                            .appendPattern("yyyy/MM")
                            .parseDefaulting(
                                    ChronoField.DAY_OF_MONTH,
                                    1
                            )
                            .toFormatter(),

                    new DateTimeFormatterBuilder()
                            .appendPattern("yyyy-MM")
                            .parseDefaulting(
                                    ChronoField.DAY_OF_MONTH,
                                    1
                            )
                            .toFormatter(),

                    new DateTimeFormatterBuilder()
                            .appendPattern("MM/yyyy")
                            .parseDefaulting(
                                    ChronoField.DAY_OF_MONTH,
                                    1
                            )
                            .toFormatter(),

                    new DateTimeFormatterBuilder()
                            .appendPattern("M/yyyy")
                            .parseDefaulting(
                                    ChronoField.DAY_OF_MONTH,
                                    1
                            )
                            .toFormatter()
            );

    private final ExcelCellValueReader cellValueReader;

    public Object convert(
            Cell cell,
            PriceListValueType type
    ) {
        if (cell == null) {
            return null;
        }

        String raw = cellValueReader.read(cell);

        if (raw == null || raw.isBlank()) {
            return null;
        }

        return switch (type) {
            case TEXT, URL -> raw.trim();

            case ISBN -> parseIsbn(cell, raw);

            case DECIMAL -> parseDecimal(cell, raw);

            case INTEGER -> parseInteger(raw);

            case DATE -> parseDate(cell, raw);
        };
    }

    public Object convert(
            String raw,
            PriceListValueType type
    ) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        return switch (type) {
            case TEXT, URL -> raw.trim();

            case ISBN -> normalizeIsbn(raw);

            case DECIMAL -> parseDecimal(raw);

            case INTEGER -> parseInteger(raw);

            case DATE -> parseTextDate(raw.trim());
        };
    }

    private String normalizeIsbn(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();

        String scientificCandidate = trimmed
                .replace(",", ".")
                .replaceAll("\\s+", "");

        if (scientificCandidate.matches(
                "[-+]?\\d+(?:\\.\\d+)?[eE][-+]?\\d+"
        )) {
            try {
                return new BigDecimal(scientificCandidate)
                        .toBigIntegerExact()
                        .toString();
            } catch (ArithmeticException | NumberFormatException ignored) {
                return null;
            }
        }

        String normalized = trimmed.replaceAll("[^0-9Xx]", "");

        return normalized.isBlank()
                ? null
                : normalized.toUpperCase(Locale.ROOT);
    }

    private String parseIsbn(Cell cell, String raw) {
        if (cell.getCellType() == CellType.NUMERIC) {
            try {
                return BigDecimal.valueOf(cell.getNumericCellValue())
                        .toBigIntegerExact()
                        .toString();
            } catch (ArithmeticException exception) {
                return normalizeIsbn(raw);
            }
        }

        return normalizeIsbn(raw);
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

        return parseDecimal(raw);
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw
                .replace("$", "")
                .replaceAll("\\s+", "")
                .trim()
                .replace("0.,", "0.")
                .replace("0,.", "0.");

        if (normalized.matches("-?[1-9]\\d{0,2}(\\.\\d{3})+(,\\d+)?")) {
            normalized = normalized
                    .replace(".", "")
                    .replace(",", ".");
        } else if (normalized.matches("-?[1-9]\\d{0,2}(,\\d{3})+(\\.\\d+)?")) {
            normalized = normalized.replace(",", "");
        } else {
            normalized = normalized.replace(",", ".");
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseInteger(String raw) {
        try {
            return new BigDecimal(
                    raw.replace(",", ".")
            ).intValue();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDate parseDate(
            Cell cell,
            String raw
    ) {
        if (
                cell.getCellType() == CellType.NUMERIC
                        && DateUtil.isCellDateFormatted(cell)
        ) {
            return cell
                    .getLocalDateTimeCellValue()
                    .toLocalDate();
        }

        return parseTextDate(raw);
    }

    private LocalDate parseTextDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value
                .trim()
                .replace(".", "/");

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(
                        normalized,
                        formatter
                );
            } catch (DateTimeParseException ignored) {
                // Se prueba el siguiente formato.
            }
        }

        return null;
    }
}