package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportJobStatusResponse;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportStartResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public interface PriceListImportService {

    PriceListImportStartResponse startImport(Long providerId, MultipartFile file, LocalDate validFrom, String idempotencyKey);

    PriceListImportJobStatusResponse getStatus(Long jobId);

    void requestCancel(Long jobId);
}