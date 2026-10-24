package com.rodrilang.librarymanager.importer.price.configuration.parser;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamingExcelDataFormatterTest {

    private final StreamingExcelDataFormatter formatter =
            new StreamingExcelDataFormatter(Locale.forLanguageTag("es-AR"));

    @Test
    void formatsNumericIsbnAsPlainDigitsEvenWhenExcelUsesScientificDisplay() {
        assertEquals("9789871234567", formatter.formatRawCellContents(9789871234567d, 11, "0.00E+00"));
    }

}
