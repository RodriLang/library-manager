package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StreamingPriceListSheetHandler
        implements XSSFSheetXMLHandler.SheetContentsHandler {

    private final PriceListImportConfig config;
    private final ConfigurableStreamingRowMapper rowMapper;
    private final Consumer<PriceListRow> consumer;

    private List<String> currentCells;
    private int currentRowIndex;

    public StreamingPriceListSheetHandler(
            PriceListImportConfig config,
            ConfigurableStreamingRowMapper rowMapper,
            Consumer<PriceListRow> consumer
    ) {
        this.config = config;
        this.rowMapper = rowMapper;
        this.consumer = consumer;
    }

    @Override
    public void startRow(int rowNum) {
        currentRowIndex = rowNum;
        currentCells = new ArrayList<>();
    }

    @Override
    public void cell(
            String cellReference,
            String formattedValue,
            XSSFComment comment
    ) {
        if (cellReference == null) {
            return;
        }

        int columnIndex =
                new CellReference(cellReference).getCol();

        ensureSize(currentCells, columnIndex + 1);

        currentCells.set(
                columnIndex,
                formattedValue == null
                        ? ""
                        : formattedValue.strip()
        );
    }

    @Override
    public void endRow(int rowNum) {
        if (rowNum < config.getFirstDataRowIndex()) {
            return;
        }

        PriceListRow row = rowMapper.map(
                rowNum,
                currentCells,
                config
        );

        consumer.accept(row);
    }

    @Override
    public void headerFooter(
            String text,
            boolean isHeader,
            String tagName
    ) {
        // Sin uso.
    }

    private void ensureSize(
            List<String> values,
            int requiredSize
    ) {
        while (values.size() < requiredSize) {
            values.add("");
        }
    }
}