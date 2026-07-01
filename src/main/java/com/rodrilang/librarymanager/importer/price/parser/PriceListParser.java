package com.rodrilang.librarymanager.importer.price.parser;

import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PriceListParser {

    boolean supports(PriceListSource priceListSource);

    List<PriceListRow> parse(MultipartFile file);
}