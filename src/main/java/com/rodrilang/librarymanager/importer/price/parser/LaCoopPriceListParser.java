package com.rodrilang.librarymanager.importer.price.parser;

import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class LaCoopPriceListParser implements PriceListParser {

    private static final int FIRST_DATA_ROW_INDEX = 1;

    private static final int TITLE_COLUMN = 0;
    private static final int AUTHOR_COLUMN = 1;
    private static final int PUBLISHER_COLUMN = 2;
    private static final int CATEGORY_COLUMN = 3;
    private static final int ISBN_COLUMN = 4;
    private static final int PRICE_COLUMN = 5;

    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    public boolean supports(PriceListSource priceListSource) {
        return PriceListSource.LA_COOP.equals(priceListSource);
    }

    @Override
    public List<PriceListRow> parse(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            List<PriceListRow> rows = new ArrayList<>();
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = FIRST_DATA_ROW_INDEX; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (row == null || isBlankRow(row)) {
                    continue;
                }

                String title = getCellValue(row, TITLE_COLUMN);
                String authorName = getCellValue(row, AUTHOR_COLUMN);
                String publisherName = getCellValue(row, PUBLISHER_COLUMN);
                String categoryName = getCellValue(row, CATEGORY_COLUMN);
                String isbn = getCellValue(row, ISBN_COLUMN);
                BigDecimal retailPrice = getPrice(row.getCell(PRICE_COLUMN));

                rows.add(new PriceListRow(
                        i + 1,
                        isbn,
                        title,
                        authorName,
                        publisherName,
                        retailPrice,
                        PriceListSource.LA_COOP,
                        categoryName,
                        BookSource.EXTERNAL_METADATA
                ));
            }

            return rows;

        } catch (Exception ex) {
            throw new BusinessException("No se pudo procesar la lista de precios de La Coop: " + ex.getMessage());
        }
    }

    private boolean isBlankRow(Row row) {
        return getCellValue(row, TITLE_COLUMN).isBlank()
                && getCellValue(row, AUTHOR_COLUMN).isBlank()
                && getCellValue(row, PUBLISHER_COLUMN).isBlank()
                && getCellValue(row, ISBN_COLUMN).isBlank()
                && getCellValue(row, PRICE_COLUMN).isBlank();
    }

    private String getCellValue(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        return dataFormatter.formatCellValue(cell).trim();
    }

    private BigDecimal getPrice(Cell cell) {
        String value = dataFormatter.formatCellValue(cell).trim();

        if (value.isBlank()) {
            return null;
        }

        String normalized = value
                .replace("$", "")
                .replace(".", "")
                .replace(",", ".")
                .trim();

        return new BigDecimal(normalized);
    }
}