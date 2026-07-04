package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.importer.price.dto.PriceListImportJobStatusResponse;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportStartResponse;
import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public interface PriceListImportService {

    PriceListImportStartResponse startImport(PriceListSource priceListSource, MultipartFile file, LocalDate validFrom, String idempotencyKey);

    PriceListImportJobStatusResponse getStatus(Long jobId);
}