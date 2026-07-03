package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.importer.price.dto.PriceListImportJobStatusResponse;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportStartResponse;
import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import org.springframework.web.multipart.MultipartFile;

public interface PriceListImportService {

    PriceListImportStartResponse startImport(PriceListSource priceListSource, MultipartFile file, String idempotencyKey);

    PriceListImportJobStatusResponse getStatus(Long jobId);
}