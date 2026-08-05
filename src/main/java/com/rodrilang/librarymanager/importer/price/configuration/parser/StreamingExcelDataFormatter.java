package com.rodrilang.librarymanager.importer.price.configuration.parser;

import org.apache.poi.ss.usermodel.DataFormatter;

import java.math.BigDecimal;
import java.util.Locale;

public class StreamingExcelDataFormatter extends DataFormatter {

    private static final double LARGE_INTEGER_THRESHOLD = 1_000_000_000D;

    public StreamingExcelDataFormatter(Locale locale) {
        super(locale);
    }

    @Override
    public String formatRawCellContents(
            double value,
            int formatIndex,
            String formatString
    ) {
        if (shouldPreserveAsPlainInteger(value)) {
            return toPlainInteger(value);
        }

        return super.formatRawCellContents(
                value,
                formatIndex,
                formatString
        );
    }

    @Override
    public String formatRawCellContents(
            double value,
            int formatIndex,
            String formatString,
            boolean use1904Windowing
    ) {
        if (shouldPreserveAsPlainInteger(value)) {
            return toPlainInteger(value);
        }

        return super.formatRawCellContents(
                value,
                formatIndex,
                formatString,
                use1904Windowing
        );
    }

    private boolean shouldPreserveAsPlainInteger(double value) {
        return Double.isFinite(value)
                && Math.abs(value) >= LARGE_INTEGER_THRESHOLD
                && value == Math.rint(value);
    }

    private String toPlainInteger(double value) {
        return BigDecimal
                .valueOf(value)
                .toBigInteger()
                .toString();
    }
}