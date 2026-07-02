package com.rodrilang.librarymanager.importer.price.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PriceParserUtils {

    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    private PriceParserUtils() {
    }

    public static BigDecimal getPrice(Cell cell) {
        if (cell == null) {
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue())
                    .setScale(2, RoundingMode.HALF_UP);
        }

        String value = DATA_FORMATTER.formatCellValue(cell).trim();

        if (value.isBlank()) {
            return null;
        }

        return parseTextPrice(value);
    }

    private static BigDecimal parseTextPrice(String value) {
        String normalized = value
                .replace("$", "")
                .replace("\u00A0", "")
                .replace(" ", "")
                .trim();

        if (normalized.isBlank()) {
            return null;
        }

        normalized = normalized.replaceAll("[^0-9.,-]", "");

        if (normalized.isBlank() || normalized.equals("-")) {
            return null;
        }

        boolean negative = normalized.startsWith("-");
        normalized = normalized.replace("-", "");

        int lastDot = normalized.lastIndexOf(".");
        int lastComma = normalized.lastIndexOf(",");

        int decimalSeparatorIndex = resolveDecimalSeparatorIndex(normalized, lastDot, lastComma);

        String numericValue;

        if (decimalSeparatorIndex >= 0) {
            String integerPart = normalized.substring(0, decimalSeparatorIndex)
                    .replace(".", "")
                    .replace(",", "");

            String decimalPart = normalized.substring(decimalSeparatorIndex + 1)
                    .replace(".", "")
                    .replace(",", "");

            numericValue = integerPart + "." + decimalPart;
        } else {
            numericValue = normalized
                    .replace(".", "")
                    .replace(",", "");
        }

        if (numericValue.isBlank() || numericValue.equals(".")) {
            return null;
        }

        if (negative) {
            numericValue = "-" + numericValue;
        }

        return new BigDecimal(numericValue)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static int resolveDecimalSeparatorIndex(
            String value,
            int lastDot,
            int lastComma
    ) {
        int lastSeparatorIndex = Math.max(lastDot, lastComma);

        if (lastSeparatorIndex < 0) {
            return -1;
        }

        int digitsAfterLastSeparator = value.length() - lastSeparatorIndex - 1;

        if (digitsAfterLastSeparator == 2) {
            return lastSeparatorIndex;
        }

        return -1;
    }
}