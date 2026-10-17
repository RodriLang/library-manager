package com.rodrilang.librarymanager.inventory.pricing.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.inventory.pricing.dto.*;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceOverviewService;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceService;
import com.rodrilang.librarymanager.inventory.pricing.service.ProviderPriceSuggestionService;
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
    private final ProviderPriceSuggestionService providerPriceSuggestionService;

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


    @GetMapping("/api/inventory/price-suggestions/books/{bookId}")
    public ProviderPriceSuggestionResponse providerPriceSuggestion(@PathVariable Long bookId) {
        return providerPriceSuggestionService.findForBook(bookId);
    }

    @PostMapping("/api/inventory/price-suggestions/batch")
    public List<ProviderPriceSuggestionResponse> providerPriceSuggestions(
            @Valid @RequestBody ProviderPriceSuggestionsRequest request
    ) {
        return providerPriceSuggestionService.findForBooks(request.bookIds());
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


    @PostMapping("/api/inventory/{inventoryId}/prices/confirm")
    public InventoryPricePointResponse confirmCurrent(
            @PathVariable Long inventoryId,
            @Valid @RequestBody ConfirmInventoryPriceRequest request
    ) {
        return priceService.confirmCurrentPrice(inventoryId, request.confirmedAt(), request.source());
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
