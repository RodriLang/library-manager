package com.rodrilang.librarymanager.importer.price.service;

import com.rodrilang.librarymanager.enums.RowValidationSeverity;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportDetailResponse;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportHistoryItemResponse;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportItemResponse;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportJobErrorResponse;
import com.rodrilang.librarymanager.importer.price.enums.EditorialPriceChange;
import com.rodrilang.librarymanager.importer.price.enums.PriceListImportItemOperation;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface PriceListImportHistoryService {

    Page<PriceListImportHistoryItemResponse> findImports(
            Long providerId,
            PriceListImportJobStatus status,
            LocalDate validFromFrom,
            LocalDate validFromTo,
            LocalDate createdFrom,
            LocalDate createdTo,
            Pageable pageable
    );

    PriceListImportDetailResponse findImport(
            Long jobId
    );

    Page<PriceListImportItemResponse> findImportItems(
            Long jobId,
            EditorialPriceChange priceChange,
            PriceListImportItemOperation operation,
            String query,
            Pageable pageable
    );

    Page<PriceListImportJobErrorResponse> findImportErrors(
            Long jobId,
            RowValidationSeverity severity,
            Pageable pageable
    );
}