package com.rodrilang.librarymanager.importer.price.parser;

import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.util.PriceParserUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class Coma4PriceListParser implements PriceListParser {

    private static final int FIRST_DATA_ROW_INDEX = 1;

    private static final int ISBN_COLUMN = 0;
    private static final int TITLE_COLUMN = 1;
    private static final int AUTHOR_COLUMN = 2;
    private static final int PUBLISHER_COLUMN = 3;
    private static final int PRICE_COLUMN = 4;

    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    public boolean supports(PriceListSource priceListSource) {
        return PriceListSource.COMA_4.equals(priceListSource);
    }

    @Override
    public void validateTemplate(Workbook workbook) {
        Sheet sheet = workbook.getSheetAt(0);

        Row headerRow = sheet.getRow(0);

        if (headerRow == null) {
            throw new BusinessException("El archivo no parece corresponder a La Coop.");
        }

        String isbnHeader = getCellValue(headerRow, ISBN_COLUMN);
        String titleHeader = getCellValue(headerRow, TITLE_COLUMN);
        String authorHeader = getCellValue(headerRow, AUTHOR_COLUMN);
        String publisherHeader = getCellValue(headerRow, PUBLISHER_COLUMN);
        String priceHeader = getCellValue(headerRow, PRICE_COLUMN);

        if (!titleHeader.toLowerCase().contains("título")
                || !authorHeader.toLowerCase().contains("autor")
                || !publisherHeader.toLowerCase().contains("editorial")
                || !isbnHeader.toLowerCase().contains("cod.barras")
                || !priceHeader.toLowerCase().contains("pvp")) {

            throw new BusinessException(
                    "El archivo seleccionado no corresponde al formato esperado para Coma 4."
            );
        }
    }

    @Override
    public List<PriceListRow> parse(Workbook workbook) {
        try {
            List<PriceListRow> rows = new ArrayList<>();
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = FIRST_DATA_ROW_INDEX; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (row == null || isBlankRow(row)) {
                    continue;
                }

                String isbn = getCellValue(row, ISBN_COLUMN);
                String title = getCellValue(row, TITLE_COLUMN);
                String authorName = getCellValue(row, AUTHOR_COLUMN);
                String publisherName = getCellValue(row, PUBLISHER_COLUMN);
                BigDecimal retailPrice = PriceParserUtils.getPrice(row.getCell(PRICE_COLUMN));

                rows.add(new PriceListRow(
                        i + 1,
                        isbn,
                        title,
                        authorName,
                        publisherName,
                        retailPrice,
                        PriceListSource.COMA_4,
                        null,
                        BookSource.EXTERNAL_METADATA
                ));
            }

            return rows;

        } catch (Exception ex) {
            throw new BusinessException("No se pudo procesar la lista de precios de Coma 4: " + ex.getMessage());
        }
    }

    private boolean isBlankRow(Row row) {
        return getCellValue(row, ISBN_COLUMN).isBlank()
                && getCellValue(row, TITLE_COLUMN).isBlank()
                && getCellValue(row, AUTHOR_COLUMN).isBlank()
                && getCellValue(row, PUBLISHER_COLUMN).isBlank()
                && getCellValue(row, PRICE_COLUMN).isBlank();
    }

    private String getCellValue(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        return dataFormatter.formatCellValue(cell).trim();
    }
}