package com.rodrilang.librarymanager.store.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.store.dto.StorefrontProductResponse;
import com.rodrilang.librarymanager.store.dto.StorefrontResponse;
import com.rodrilang.librarymanager.store.dto.StorefrontFiltersResponse;
import com.rodrilang.librarymanager.store.service.StorefrontService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/storefront")
@RequiredArgsConstructor
public class StorefrontController {
    private final StorefrontService service;

    @GetMapping("/resolve")
    public StorefrontResponse resolve(@RequestParam String host) { return service.resolve(host); }

    @GetMapping("/{storeId}/products")
    public PageResponse<StorefrontProductResponse> products(
            @PathVariable UUID storeId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<Long> authorIds,
            @RequestParam(required = false) List<Long> publisherIds,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(defaultValue = "RELEVANCE") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        return service.products(storeId, q, authorIds, publisherIds, category, genre, minPrice, maxPrice, inStock, featured, sort, page, size);
    }

    @GetMapping("/{storeId}/filters")
    public StorefrontFiltersResponse filters(@PathVariable UUID storeId) {
        return service.filters(storeId);
    }

    @GetMapping("/{storeId}/products/{inventoryId}")
    public StorefrontProductResponse product(@PathVariable UUID storeId, @PathVariable Long inventoryId) {
        return service.product(storeId, inventoryId);
    }
}
