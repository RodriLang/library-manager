package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.importer.price.dto.PriceListImportResult;
import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import org.springframework.web.multipart.MultipartFile;

public interface PriceListImportService {

    PriceListImportResult importPriceList(PriceListSource priceListSource, MultipartFile file);
}