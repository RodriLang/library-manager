package com.rodrilang.librarymanager.inventory.pricing.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.inventory.pricing.dto.*;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceImportApplyService;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceImportService;
import com.rodrilang.librarymanager.inventory.pricing.job.service.InventoryPriceImportJobService;
import com.rodrilang.librarymanager.inventory.pricing.job.dto.InventoryPriceImportJobResponse;
import com.rodrilang.librarymanager.inventory.pricing.job.model.InventoryPriceImportJobType;

import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/inventory/price-imports")
@RequiredArgsConstructor
public class InventoryPriceImportController {

    private final InventoryPriceImportService service;
    private final InventoryPriceImportApplyService applyService;
    private final InventoryPriceImportJobService jobService;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InventoryPriceImportPreviewStartResponse> preview(
            @RequestParam Long formatId,
            @RequestParam(required = false) String sourceName,
            @RequestParam(required = false) Long providerId,
            @RequestParam(defaultValue = "true") boolean useFormatProvider,
            @RequestParam LocalDate effectiveFrom,
            @RequestParam MultipartFile file
    ) {
        return ResponseEntity.accepted().body(service.startPreview(formatId, sourceName, effectiveFrom, file, providerId, useFormatProvider));
    }

    @PostMapping("/{importId}/duplicates/{inventoryId}/resolve")
    public InventoryPriceImportPreviewResponse resolveDuplicate(
            @PathVariable Long importId,
            @PathVariable Long inventoryId,
            @Valid @RequestBody ResolveInventoryPriceImportDuplicateRequest request
    ) {
        return service.resolveDuplicate(importId, inventoryId, request);
    }

    @GetMapping("/{importId}")
    public InventoryPriceImportPreviewResponse get(@PathVariable Long importId) {
        return service.get(importId);
    }

    @PostMapping("/{importId}/apply")
    public ResponseEntity<InventoryPriceImportApplyStartResponse> apply(
            @PathVariable Long importId,
            @RequestBody(required = false) ApplyInventoryPriceImportRequest request
    ) {
        return ResponseEntity.accepted().body(applyService.start(importId, request));
    }

    @GetMapping("/{importId}/status")
    public InventoryPriceImportProcessingStatusResponse status(@PathVariable Long importId) {
        return applyService.status(importId);
    }


    @GetMapping("/{importId}/jobs")
    public List<InventoryPriceImportJobResponse> jobs(@PathVariable Long importId) {
        service.assertAccessible(importId);
        return jobService.list(importId);
    }

    @PostMapping("/{importId}/jobs/{type}/retry")
    public InventoryPriceImportJobResponse retryJob(
            @PathVariable Long importId,
            @PathVariable InventoryPriceImportJobType type
    ) {
        service.assertAccessible(importId);
        return jobService.retry(importId, type);
    }

    @PostMapping("/{importId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long importId) {
        service.cancel(importId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/active")
    public List<InventoryPriceImportActivityResponse> active(
            @RequestParam(required = false) Long providerId
    ) {
        return service.active(providerId);
    }

    @GetMapping
    public PageResponse<InventoryPriceImportHistoryResponse> history(
            @RequestParam(required = false) Long providerId,
            Pageable pageable
    ) {
        return service.history(providerId, pageable);
    }
}
