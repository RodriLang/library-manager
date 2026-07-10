package com.rodrilang.librarymanager.importer.price.parser;

import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.util.PriceParserUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
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
    public void validateTemplate(Workbook workbook) {
        Sheet sheet = workbook.getSheetAt(0);
        Row headerRow = sheet.getRow(0);

        if (headerRow == null) {
            throw new BusinessException(
                    "El archivo seleccionado no corresponde al formato esperado para La Coop."
            );
        }

        String titleHeader = getNormalizedCellValue(headerRow, TITLE_COLUMN);
        String authorHeader = getNormalizedCellValue(headerRow, AUTHOR_COLUMN);
        String publisherHeader = getNormalizedCellValue(headerRow, PUBLISHER_COLUMN);
        String categoryHeader = getNormalizedCellValue(headerRow, CATEGORY_COLUMN);
        String isbnHeader = getNormalizedCellValue(headerRow, ISBN_COLUMN);
        String priceHeader = getNormalizedCellValue(headerRow, PRICE_COLUMN);

        log.info(
                "LA_COOP headers: title='{}', author='{}', publisher='{}', category='{}', isbn='{}', price='{}'",
                titleHeader,
                authorHeader,
                publisherHeader,
                categoryHeader,
                isbnHeader,
                priceHeader
        );

        boolean valid =
                titleHeader.contains("titulo")
                        && authorHeader.contains("autor")
                        && publisherHeader.contains("editor")
                        && categoryHeader.contains("genero")
                        && isbnHeader.contains("isbn")
                        && priceHeader.contains("precio");

        if (!valid) {
            throw new BusinessException(
                    "El archivo seleccionado no corresponde al formato esperado para La Coop."
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

                String title = getCellValue(row, TITLE_COLUMN);
                String authorName = getCellValue(row, AUTHOR_COLUMN);
                String publisherName = getCellValue(row, PUBLISHER_COLUMN);
                String categoryName = getCellValue(row, CATEGORY_COLUMN);
                String isbn = getCellValue(row, ISBN_COLUMN);
                BigDecimal retailPrice = PriceParserUtils.getPrice(row.getCell(PRICE_COLUMN));

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

    private String getNormalizedCellValue(Row row, int columnIndex) {
        return getCellValue(row, columnIndex)
                .toLowerCase()
                .trim();
    }
}