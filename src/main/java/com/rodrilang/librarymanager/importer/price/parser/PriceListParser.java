package com.rodrilang.librarymanager.importer.price.parser;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.List;

public interface PriceListParser {

    boolean supports(PriceListImportConfig config);

    List<PriceListRow> parse(
            Workbook workbook,
            PriceListImportConfig config
    );
}