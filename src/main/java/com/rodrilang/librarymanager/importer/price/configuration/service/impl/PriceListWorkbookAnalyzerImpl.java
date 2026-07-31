package com.rodrilang.librarymanager.importer.price.configuration.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListPreviewRowResponse;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListSheetAnalysisResponse;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListWorkbookAnalysisResponse;
import com.rodrilang.librarymanager.importer.price.configuration.service.PriceListWorkbookAnalyzer;
import com.rodrilang.librarymanager.importer.price.util.ExcelCellValueReader;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PriceListWorkbookAnalyzerImpl implements PriceListWorkbookAnalyzer {

    private static final int MAX_PREVIEW_ROWS = 30;

    private static final Set<String> HEADER_KEYWORDS = Set.of(
            "isbn",
            "ean",
            "codigo",
            "código",
            "barra",
            "titulo",
            "título",
            "autor",
            "editorial",
            "sello",
            "pvp",
            "precio",
            "genero",
            "género",
            "paginas",
            "páginas",
            "idioma",
            "sinopsis",
            "descripcion",
            "descripción",
            "stock"
    );

    private final ExcelCellValueReader cellValueReader;

    @Override
    public PriceListWorkbookAnalysisResponse analyze(MultipartFile file) {
        validateFile(file);

        try (
                InputStream inputStream = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(inputStream)
        ) {

            List<PriceListSheetAnalysisResponse> sheets =
                    new ArrayList<>();

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheets.add(analyzeSheet(workbook.getSheetAt(i), i));
            }

            return new PriceListWorkbookAnalysisResponse(file.getOriginalFilename(), sheets);

        } catch (Exception ex) {
            throw new BusinessException("No se pudo analizar el archivo Excel: " + ex.getMessage());
        }
    }

    private PriceListSheetAnalysisResponse analyzeSheet(Sheet sheet, int sheetIndex) {

        int columnCount = findMaximumColumnCount(sheet);

        List<PriceListPreviewRowResponse> preview = readPreviewRows(sheet, columnCount);

        Integer suggestedHeaderRowIndex = detectHeaderRow(preview);

        return new PriceListSheetAnalysisResponse(
                sheetIndex,
                sheet.getSheetName(),
                calculatePhysicalRowCount(sheet),
                columnCount,
                suggestedHeaderRowIndex,
                preview
        );
    }

    private List<PriceListPreviewRowResponse> readPreviewRows(Sheet sheet, int columnCount) {
        List<PriceListPreviewRowResponse> rows = new ArrayList<>();

        int lastPreviewRow = Math.min(sheet.getLastRowNum(), MAX_PREVIEW_ROWS - 1);

        for (int rowIndex = 0; rowIndex <= lastPreviewRow; rowIndex++) {

            Row row = sheet.getRow(rowIndex);

            List<String> cells = new ArrayList<>();

            for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                cells.add(getCellValue(row, columnIndex));
            }

            rows.add(new PriceListPreviewRowResponse(rowIndex, cells));
        }

        return rows;
    }

    private int findMaximumColumnCount(Sheet sheet) {
        int maxColumnCount = 0;

        int lastRowIndex = Math.min(
                sheet.getLastRowNum(),
                MAX_PREVIEW_ROWS - 1
        );

        for (int rowIndex = 0;
             rowIndex <= lastRowIndex;
             rowIndex++) {

            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                continue;
            }

            int lastNonBlankColumn = findLastNonBlankColumn(row);

            maxColumnCount = Math.max(
                    maxColumnCount,
                    lastNonBlankColumn + 1
            );
        }

        return maxColumnCount;
    }

    private int findLastNonBlankColumn(Row row) {
        int lastNonBlankColumn = -1;

        for (Cell cell : row) {
            String value = cellValueReader.read(cell);

            if (!value.isBlank()) {
                lastNonBlankColumn = Math.max(
                        lastNonBlankColumn,
                        cell.getColumnIndex()
                );
            }
        }

        return lastNonBlankColumn;
    }

    private Integer detectHeaderRow(List<PriceListPreviewRowResponse> rows) {
        Integer bestRow = null;
        int bestScore = 0;

        for (PriceListPreviewRowResponse row : rows) {

            int score = calculateHeaderScore(row.cells());

            if (score > bestScore) {
                bestScore = score;
                bestRow = row.rowIndex();
            }
        }

        return bestScore >= 2 ? bestRow : null;
    }

    private int calculateHeaderScore(List<String> cells) {
        int score = 0;

        for (String cell : cells) {
            String normalized = normalize(cell);

            if (normalized.isBlank()) {
                continue;
            }

            boolean matches = HEADER_KEYWORDS
                    .stream()
                    .anyMatch(normalized::contains);

            if (matches) {
                score++;
            }
        }

        return score;
    }

    private int calculatePhysicalRowCount(Sheet sheet) {
        if (sheet.getPhysicalNumberOfRows() == 0) {
            return 0;
        }

        return sheet.getLastRowNum() + 1;
    }

    private String getCellValue(Row row, int columnIndex) {
        if (row == null) {
            return "";
        }

        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);

        if (cell == null) {
            return "";
        }

        return cellValueReader.read(cell);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Debe seleccionar un archivo Excel.");
        }

        String fileName = file.getOriginalFilename();

        if (fileName == null) {
            throw new BusinessException("No se pudo determinar el nombre del archivo.");
        }

        String normalized = fileName.toLowerCase(Locale.ROOT);

        if (!normalized.endsWith(".xls") && !normalized.endsWith(".xlsx")) {
            throw new BusinessException("El archivo debe tener formato .xls o .xlsx.");
        }
    }
}