package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListColumnMapping;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fast SAX reader used by inventory price imports when the configured mappings
 * do not require Excel date/style interpretation.
 *
 * <p>Unlike {@code XSSFSheetXMLHandler}, this handler only decodes configured
 * columns and does not run {@code DataFormatter} for every cell in the sheet.
 * This is important for large provider lists where only a handful of columns
 * are relevant.</p>
 */
@Slf4j
final class FastMappedPriceListSheetHandler extends DefaultHandler {

    private final PriceListImportConfig config;
    private final ConfigurableStreamingRowMapper rowMapper;
    private final Consumer<PriceListRow> consumer;
    private final ReadOnlySharedStringsTable sharedStrings;
    private final BitSet selectedColumns;
    private final int maxSelectedColumn;

    private long emittedRows;
    private long callbackNanos;
    private long mappingNanos;
    private long consumerNanos;
    private long decodeNanos;
    private long decodedCells;
    private final long startedNanos = System.nanoTime();

    private List<String> currentCells;
    private int currentRowIndex = -1;
    private int currentColumnIndex = -1;
    private String currentCellType;
    private boolean selectedCell;
    private boolean collectingValue;
    private boolean collectingInlineText;
    private final StringBuilder valueBuffer = new StringBuilder(64);
    private final StringBuilder inlineTextBuffer = new StringBuilder(64);

    FastMappedPriceListSheetHandler(
            PriceListImportConfig config,
            ConfigurableStreamingRowMapper rowMapper,
            Consumer<PriceListRow> consumer,
            ReadOnlySharedStringsTable sharedStrings
    ) {
        this.config = config;
        this.rowMapper = rowMapper;
        this.consumer = consumer;
        this.sharedStrings = sharedStrings;

        BitSet columns = new BitSet();
        int maxColumn = -1;
        for (PriceListColumnMapping mapping : config.getMappings()) {
            if (!mapping.isActive() || mapping.getColumnIndex() == null || mapping.getColumnIndex() < 0) {
                continue;
            }
            columns.set(mapping.getColumnIndex());
            maxColumn = Math.max(maxColumn, mapping.getColumnIndex());
        }
        this.selectedColumns = columns;
        this.maxSelectedColumn = maxColumn;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        switch (qName) {
            case "row" -> startRow(attributes);
            case "c" -> startCell(attributes);
            case "v" -> {
                if (selectedCell) {
                    collectingValue = true;
                    valueBuffer.setLength(0);
                }
            }
            case "t" -> {
                if (selectedCell && "inlineStr".equals(currentCellType)) {
                    collectingInlineText = true;
                    // Do not clear here: an inline rich string can contain multiple <t> nodes.
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (collectingValue) {
            valueBuffer.append(ch, start, length);
        }
        if (collectingInlineText) {
            inlineTextBuffer.append(ch, start, length);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        switch (qName) {
            case "v" -> {
                if (selectedCell) {
                    collectingValue = false;
                }
            }
            case "t" -> {
                if (selectedCell && "inlineStr".equals(currentCellType)) {
                    collectingInlineText = false;
                }
            }
            case "c" -> endCell();
            case "row" -> endRow();
            default -> {
            }
        }
    }

    private void startRow(Attributes attributes) {
        String rowRef = attributes.getValue("r");
        if (rowRef != null) {
            try {
                currentRowIndex = Integer.parseInt(rowRef) - 1;
            } catch (NumberFormatException ignored) {
                currentRowIndex++;
            }
        } else {
            currentRowIndex++;
        }

        if (currentRowIndex >= config.getFirstDataRowIndex()) {
            currentCells = new ArrayList<>(Math.max(0, maxSelectedColumn + 1));
            ensureSize(currentCells, maxSelectedColumn + 1);
        } else {
            currentCells = null;
        }
    }

    private void startCell(Attributes attributes) {
        currentCellType = attributes.getValue("t");
        currentColumnIndex = columnIndex(attributes.getValue("r"));
        selectedCell = currentCells != null
                && currentColumnIndex >= 0
                && selectedColumns.get(currentColumnIndex);
        collectingValue = false;
        collectingInlineText = false;
        valueBuffer.setLength(0);
        inlineTextBuffer.setLength(0);
    }

    private void endCell() {
        if (selectedCell) {
            long decodeStarted = System.nanoTime();
            try {
                currentCells.set(currentColumnIndex, decodeCellValue());
            } finally {
                decodeNanos += System.nanoTime() - decodeStarted;
                decodedCells++;
            }
        }
        currentColumnIndex = -1;
        currentCellType = null;
        selectedCell = false;
        collectingValue = false;
        collectingInlineText = false;
    }

    private void endRow() {
        if (currentCells == null || currentRowIndex < config.getFirstDataRowIndex()) {
            return;
        }
        long callbackStarted = System.nanoTime();
        try {
            long mappingStarted = System.nanoTime();
            PriceListRow row = rowMapper.map(currentRowIndex, currentCells, config);
            mappingNanos += System.nanoTime() - mappingStarted;
            long consumerStarted = System.nanoTime();
            try {
                consumer.accept(row);
            } finally {
                consumerNanos += System.nanoTime() - consumerStarted;
            }
        } finally {
            callbackNanos += System.nanoTime() - callbackStarted;
            emittedRows++;
            if (emittedRows % 5_000 == 0) {
                log.info("PRICE LIST PARSER PROGRESS mode=FAST_SAX rows={} elapsedMs={} callbackMs={} mappingMs={} consumerMs={} decodeMs={} decodedCells={} rowIndex={}",
                        emittedRows, (System.nanoTime() - startedNanos) / 1_000_000L,
                        callbackNanos / 1_000_000L, mappingNanos / 1_000_000L,
                        consumerNanos / 1_000_000L, decodeNanos / 1_000_000L, decodedCells, currentRowIndex);
            }
        }
    }

    @Override
    public void endDocument() {
        long elapsed = System.nanoTime() - startedNanos;
        log.info("PRICE LIST PARSER ROWS DONE mode=FAST_SAX rows={} elapsedMs={} callbackMs={} mappingMs={} consumerMs={} decodeMs={} otherSaxMs={} decodedCells={}",
                emittedRows, elapsed / 1_000_000L,
                callbackNanos / 1_000_000L, mappingNanos / 1_000_000L,
                consumerNanos / 1_000_000L, decodeNanos / 1_000_000L,
                Math.max(0L, (elapsed - callbackNanos - decodeNanos)) / 1_000_000L, decodedCells);
    }

    private String decodeCellValue() {
        if ("inlineStr".equals(currentCellType)) {
            return inlineTextBuffer.toString().strip();
        }

        String raw = valueBuffer.toString();
        if (raw.isEmpty()) {
            return "";
        }

        if ("s".equals(currentCellType)) {
            try {
                int index = Integer.parseInt(raw);
                return sharedStrings.getItemAt(index).getString().strip();
            } catch (RuntimeException exception) {
                return "";
            }
        }

        return raw.strip();
    }

    private int columnIndex(String cellReference) {
        if (cellReference == null || cellReference.isEmpty()) {
            return -1;
        }

        int column = 0;
        int letters = 0;
        for (int i = 0; i < cellReference.length(); i++) {
            char c = cellReference.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                column = column * 26 + (c - 'A' + 1);
                letters++;
            } else if (c >= 'a' && c <= 'z') {
                column = column * 26 + (c - 'a' + 1);
                letters++;
            } else {
                break;
            }
        }
        return letters == 0 ? -1 : column - 1;
    }

    private void ensureSize(List<String> values, int requiredSize) {
        while (values.size() < requiredSize) {
            values.add("");
        }
    }
}
