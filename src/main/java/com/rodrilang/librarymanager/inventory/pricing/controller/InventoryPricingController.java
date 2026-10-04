package com.rodrilang.librarymanager.inventory.pricing.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.inventory.pricing.dto.*;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceOverviewService;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class InventoryPricingController {

    private final InventoryPriceService priceService;
    private final InventoryPriceOverviewService overviewService;

    @GetMapping("/api/inventory/prices")
    public PageResponse<InventoryPriceOverviewResponse> find(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) Integer staleDays,
            Pageable pageable
    ) {
        return PageResponse.of(overviewService.find(q, status, staleDays, pageable));
    }

    @GetMapping("/api/inventory/prices/summary")
    public InventoryPriceDashboardSummaryResponse summary(
            @RequestParam(required = false) Integer staleDays
    ) {
        return overviewService.summary(staleDays);
    }

    @GetMapping("/api/inventory/{inventoryId}/prices")
    public List<InventoryPricePointResponse> history(@PathVariable Long inventoryId) {
        return priceService.history(inventoryId);
    }

    @PostMapping("/api/inventory/{inventoryId}/prices")
    public ResponseEntity<InventoryPricePointResponse> create(
            @PathVariable Long inventoryId,
            @Valid @RequestBody CreateInventoryPriceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(priceService.upsertManual(inventoryId, request.amount(), request.effectiveFrom()));
    }

    @DeleteMapping("/api/inventory/{inventoryId}/prices/{priceId}")
    public ResponseEntity<Void> deleteFuture(
            @PathVariable Long inventoryId,
            @PathVariable Long priceId
    ) {
        priceService.deleteFuture(inventoryId, priceId);
        return ResponseEntity.noContent().build();
    }
}
