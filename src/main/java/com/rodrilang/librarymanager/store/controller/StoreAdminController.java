package com.rodrilang.librarymanager.store.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.store.dto.*;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.enums.InventoryPriceMode;
import com.rodrilang.librarymanager.enums.InventoryStockFilter;

import java.util.List;
import com.rodrilang.librarymanager.store.service.StoreAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StoreAdminController {
    private final StoreAdminService service;

    @GetMapping("/settings")
    public StoreSettingsResponse settings() { return service.getOrCreateSettings(); }

    @PutMapping("/settings")
    public StoreSettingsResponse updateSettings(@Valid @RequestBody StoreSettingsRequest request) { return service.updateSettings(request); }

    @PostMapping("/domains")
    public StoreDomainResponse addDomain(@Valid @RequestBody CreateStoreDomainRequest request) {
        return service.addCustomDomain(request.hostname());
    }

    @DeleteMapping("/domains/{domainId}")
    public void removeDomain(@PathVariable Long domainId) {
        service.removeCustomDomain(domainId);
    }

    @GetMapping("/products")
    public PageResponse<StoreProductAdminResponse> products(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(defaultValue = "ALL") InventoryStockFilter stock,
            @RequestParam(required = false) BookCondition condition,
            @RequestParam(required = false) List<Long> publisherIds,
            @RequestParam(required = false) List<Long> authorIds,
            @RequestParam(defaultValue = "ALL") InventoryPriceMode priceMode,
            @RequestParam(required = false) Boolean consignment,
            Pageable pageable
    ) {
        StoreProductAdminFilters filters = new StoreProductAdminFilters(
                q, published, featured, stock, condition, publisherIds, authorIds, priceMode, consignment
        );
        return PageResponse.of(service.products(filters, pageable));
    }

    @PatchMapping("/products/bulk")
    public StorePublicationBulkUpdateResponse bulkUpdatePublications(
            @Valid @RequestBody BulkUpdateStorePublicationsRequest request
    ) {
        return service.bulkUpdatePublications(request);
    }

    @PatchMapping("/products/{inventoryId}")
    public StoreProductAdminResponse updatePublication(
            @PathVariable Long inventoryId,
            @Valid @RequestBody UpdateStorePublicationRequest request
    ) { return service.updatePublication(inventoryId, request); }
}
