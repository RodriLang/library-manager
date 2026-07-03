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

import static org.apache.commons.lang3.StringUtils.containsAny;

@Slf4j
@Component
public class FcePriceListParser implements PriceListParser {

    private static final int FIRST_DATA_ROW_INDEX = 4;

    private static final int ISBN_COLUMN = 0;
    private static final int TITLE_COLUMN = 1;
    private static final int AUTHOR_COLUMN = 2;
    private static final int PRICE_COLUMN = 5;

    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    public boolean supports(PriceListSource priceListSource) {
        return PriceListSource.FCE.equals(priceListSource);
    }

    @Override
    public void validateTemplate(Workbook workbook) {
        Sheet sheet = workbook.getSheetAt(0);
        Row headerRow = sheet.getRow(3);

        if (headerRow == null) {
            throw new BusinessException("El archivo seleccionado no corresponde al formato esperado para FCE.");
        }

        String isbnHeader = getNormalizedCellValue(headerRow, ISBN_COLUMN);
        String titleHeader = getNormalizedCellValue(headerRow, TITLE_COLUMN);
        String authorHeader = getNormalizedCellValue(headerRow, AUTHOR_COLUMN);
        String priceHeader = getNormalizedCellValue(headerRow, PRICE_COLUMN);

        boolean valid =
                containsAny(isbnHeader, "cod.barra", "codigo", "código", "barra", "isbn")
                        && containsAny(titleHeader, "titulo", "título")
                        && containsAny(authorHeader, "autor")
                        && containsAny(priceHeader, "pvp", "precio");

        if (!valid) {
            throw new BusinessException(
                    "El archivo seleccionado no corresponde al formato esperado para FCE."
            );
        }
    }

    @Override
    public List<PriceListRow> parse(Workbook workbook) {
        try {
            List<PriceListRow> rows = new ArrayList<>();
            var sheet = workbook.getSheetAt(0);

            for (int i = FIRST_DATA_ROW_INDEX; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (row == null || isBlankRow(row)) {
                    continue;
                }

                String isbn = getCellValue(row, ISBN_COLUMN);
                String title = getCellValue(row, TITLE_COLUMN);
                String authorName = getCellValue(row, AUTHOR_COLUMN);
                BigDecimal retailPrice = PriceParserUtils.getPrice(row.getCell(PRICE_COLUMN));

                rows.add(new PriceListRow(
                        i + 1,
                        isbn,
                        title,
                        authorName,
                        "Fondo de Cultura Económica",
                        retailPrice,
                        PriceListSource.FCE,
                        null,
                        BookSource.EXTERNAL_METADATA
                ));
            }

            return rows;

        } catch (Exception ex) {
            throw new BusinessException("No se pudo procesar la lista de precios de FCE: " + ex.getMessage());
        }
    }

    private boolean isBlankRow(Row row) {
        return getCellValue(row, ISBN_COLUMN).isBlank()
                && getCellValue(row, TITLE_COLUMN).isBlank()
                && getCellValue(row, AUTHOR_COLUMN).isBlank()
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