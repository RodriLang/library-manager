package com.rodrilang.librarymanager.importer.price.configuration.analysis;

import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListPreviewRowResponse;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.internal.PriceListSheetPreview;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;

import java.util.ArrayList;
import java.util.List;

public class PriceListSheetPreviewHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

    private final int sheetIndex;
    private final String sheetName;
    private final int maxRows;
    private final int maxColumns;

    private final List<PriceListPreviewRowResponse> rows =
            new ArrayList<>();

    private List<String> currentCells;
    private int currentRowIndex;
    private int columnCount;
    private boolean truncated;

    public PriceListSheetPreviewHandler(
            int sheetIndex,
            String sheetName,
            int maxRows,
            int maxColumns
    ) {
        this.sheetIndex = sheetIndex;
        this.sheetName = sheetName;
        this.maxRows = maxRows;
        this.maxColumns = maxColumns;
    }

    @Override
    public void startRow(int rowNum) {
        if (rowNum >= maxRows) {
            truncated = true;
            throw new PreviewLimitReachedException();
        }

        currentRowIndex = rowNum;
        currentCells = new ArrayList<>();
    }

    @Override
    public void endRow(int rowNum) {
        rows.add(
                new PriceListPreviewRowResponse(
                        currentRowIndex,
                        List.copyOf(currentCells)
                )
        );
    }

    @Override
    public void cell(
            String cellReference,
            String formattedValue,
            XSSFComment comment
    ) {
        if (cellReference == null || currentCells == null) {
            return;
        }

        int columnIndex =
                new CellReference(cellReference).getCol();

        if (columnIndex >= maxColumns) {
            return;
        }

        ensureSize(currentCells, columnIndex + 1);

        String value = formattedValue == null
                ? ""
                : formattedValue.strip();

        currentCells.set(columnIndex, value);

        columnCount = Math.max(
                columnCount,
                columnIndex + 1
        );
    }

    @Override
    public void headerFooter(
            String text,
            boolean isHeader,
            String tagName
    ) {
        // No se utiliza.
    }

    public PriceListSheetPreview toResult() {
        List<PriceListPreviewRowResponse> normalizedRows =
                rows.stream()
                        .map(this::normalizeRowSize)
                        .toList();

        return new PriceListSheetPreview(
                sheetIndex,
                sheetName,
                normalizedRows.size(),
                columnCount,
                truncated,
                normalizedRows
        );
    }

    private PriceListPreviewRowResponse normalizeRowSize(
            PriceListPreviewRowResponse row
    ) {
        List<String> cells = new ArrayList<>(row.cells());

        ensureSize(cells, columnCount);

        return new PriceListPreviewRowResponse(
                row.rowIndex(),
                List.copyOf(cells)
        );
    }

    private void ensureSize(
            List<String> values,
            int expectedSize
    ) {
        while (values.size() < expectedSize) {
            values.add("");
        }
    }
}