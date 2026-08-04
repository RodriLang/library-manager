package com.rodrilang.librarymanager.importer.price.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.springframework.stereotype.Component;

@Component
public class ExcelCellValueReader {

    private final DataFormatter dataFormatter = new DataFormatter();

    public String read(Cell cell) {
        if (cell == null) {
            return "";
        }

        if (cell.getCellType() == CellType.NUMERIC) {

            if (DateUtil.isCellDateFormatted(cell)) {
                return dataFormatter
                        .formatCellValue(cell)
                        .trim();
            }

            String formatted = dataFormatter
                    .formatCellValue(cell)
                    .trim();

            if (isScientificNotation(formatted)) {
                return NumberToTextConverter
                        .toText(cell.getNumericCellValue())
                        .trim();
            }

            return formatted;
        }

        return dataFormatter
                .formatCellValue(cell)
                .trim();
    }

    private boolean isScientificNotation(String value) {
        return value.contains("E") || value.contains("e");
    }
}