package com.rodrilang.librarymanager.importer.price.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.enums.RowValidationSeverity;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportDetailResponse;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportHistoryItemResponse;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportItemResponse;
import com.rodrilang.librarymanager.importer.price.dto.response.PriceListImportJobErrorResponse;
import com.rodrilang.librarymanager.importer.price.enums.EditorialPriceChange;
import com.rodrilang.librarymanager.importer.price.enums.PriceListImportItemOperation;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobStatus;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportHistoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Historial de listas de precios", description = "Consulta de importaciones anteriores y precios procesados")
@RestController
@RequestMapping("/api/price-lists/imports")
@RequiredArgsConstructor
public class PriceListImportHistoryController {

    private final PriceListImportHistoryService historyService;

    @GetMapping
    public ResponseEntity<PageResponse<PriceListImportHistoryItemResponse>> findImports(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) PriceListImportJobStatus status,
            @RequestParam(required = false) LocalDate validFromFrom,
            @RequestParam(required = false) LocalDate validFromTo,
            @RequestParam(required = false) LocalDate createdFrom,
            @RequestParam(required = false) LocalDate createdTo,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        return ResponseEntity.ok(
                PageResponse.of(
                        historyService.findImports(
                                providerId,
                                status,
                                validFromFrom,
                                validFromTo,
                                createdFrom,
                                createdTo,
                                pageable
                        )
                )
        );
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<PriceListImportDetailResponse> findImport(
            @PathVariable Long jobId
    ) {
        return ResponseEntity.ok(
                historyService.findImport(jobId)
        );
    }

    @GetMapping("/{jobId}/items")
    public ResponseEntity<PageResponse<PriceListImportItemResponse>> findImportItems(
            @PathVariable Long jobId,
            @RequestParam(required = false) EditorialPriceChange priceChange,
            @RequestParam(required = false) PriceListImportItemOperation operation,
            @RequestParam(required = false) String query,
            @PageableDefault(
                    size = 25,
                    sort = "id",
                    direction = Sort.Direction.ASC
            ) Pageable pageable
    ) {
        return ResponseEntity.ok(
                PageResponse.of(
                        historyService.findImportItems(
                                jobId,
                                priceChange,
                                operation,
                                query,
                                pageable
                        )
                )
        );
    }

    @GetMapping("/{jobId}/errors")
    public ResponseEntity<PageResponse<PriceListImportJobErrorResponse>> findImportErrors(
            @PathVariable Long jobId,
            @RequestParam(required = false) RowValidationSeverity severity,
            @PageableDefault(
                    size = 25,
                    sort = "rowNumber",
                    direction = Sort.Direction.ASC
            ) Pageable pageable
    ) {
        return ResponseEntity.ok(
                PageResponse.of(
                        historyService.findImportErrors(
                                jobId,
                                severity,
                                pageable
                        )
                )
        );
    }
}