package com.rodrilang.librarymanager.importer.price.parser;

import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.List;

public interface PriceListParser {

    boolean supports(PriceListSource priceListSource);

    void validateTemplate(Workbook workbook);

    List<PriceListRow> parse(Workbook workbook);
}