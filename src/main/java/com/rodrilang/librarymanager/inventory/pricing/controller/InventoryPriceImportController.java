package com.rodrilang.librarymanager.inventory.pricing.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.inventory.pricing.dto.*;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceImportService;
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

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InventoryPriceImportPreviewResponse preview(
            @RequestParam Long formatId,
            @RequestParam(required = false) String sourceName,
            @RequestParam LocalDate effectiveFrom,
            @RequestParam MultipartFile file
    ) {
        return service.preview(formatId, sourceName, effectiveFrom, file);
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
    public InventoryPriceImportApplyResponse apply(
            @PathVariable Long importId,
            @RequestBody(required = false) ApplyInventoryPriceImportRequest request
    ) {
        return service.apply(importId, request);
    }

    @PostMapping("/{importId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long importId) {
        service.cancel(importId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public PageResponse<InventoryPriceImportHistoryResponse> history(Pageable pageable) {
        return service.history(pageable);
    }
}
