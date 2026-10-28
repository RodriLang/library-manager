package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.exception.BusinessException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.ss.usermodel.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared XLS support; existing low-memory XLSX readers remain unchanged. */
public final class LegacyXlsSupport {
    private LegacyXlsSupport() {}

    public static boolean isXls(Path file) throws IOException {
        try (InputStream input = FileMagic.prepareToCheckMagic(Files.newInputStream(file))) {
            FileMagic magic = FileMagic.valueOf(input);
            if (magic == FileMagic.OLE2) return true;
            if (magic == FileMagic.OOXML) return false;
            throw new BusinessException("El archivo no es un Excel .xls o .xlsx válido.");
        }
    }

    public static HSSFWorkbook open(Path file) throws IOException {
        return new HSSFWorkbook(Files.newInputStream(file));
    }

    public static List<String> cells(Row row, int maxColumns, DataFormatter formatter, FormulaEvaluator evaluator) {
        int count = Math.min(Math.max(0, row.getLastCellNum()), maxColumns);
        List<String> values = new ArrayList<>(count);
        for (int column = 0; column < count; column++) {
            Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String value = cell == null ? "" : formatter.formatCellValue(cell, evaluator);
            values.add(value == null ? "" : value.strip());
        }
        return values;
    }

    public static String extension(String filename) {
        String normalized = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".xlsx")) return ".xlsx";
        if (normalized.endsWith(".xls")) return ".xls";
        throw new BusinessException("Se requiere un archivo Excel .xls o .xlsx.");
    }
}
