package com.rodrilang.librarymanager.importer.price.configuration.parser;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ConfigurablePriceListParser {

    private final PriceListSheetResolver sheetResolver;

    private final ConfigurablePriceListTemplateValidator
            templateValidator;

    private final ConfigurablePriceListRowMapper rowMapper;

    private final ConfigurablePriceListRowInspector
            rowInspector;

    public List<PriceListRow> parse(
            Workbook workbook,
            PriceListImportConfig config
    ) {
        try {
            Sheet sheet = sheetResolver.resolve(
                    workbook,
                    config
            );

            templateValidator.validate(
                    sheet,
                    config
            );

            List<PriceListRow> rows =
                    new ArrayList<>();

            for (
                    int rowIndex =
                    config.getFirstDataRowIndex();

                    rowIndex <= sheet.getLastRowNum();

                    rowIndex++
            ) {

                Row row = sheet.getRow(rowIndex);

                if (rowInspector.isBlank(row, config)) {
                    continue;
                }

                rows.add(
                        rowMapper.map(
                                row,
                                config
                        )
                );
            }

            return rows;

        } catch (BusinessException ex) {
            throw ex;

        } catch (Exception ex) {
            throw new BusinessException(
                    "No se pudo procesar la lista de precios: "
                            + ex.getMessage()
            );
        }
    }
}