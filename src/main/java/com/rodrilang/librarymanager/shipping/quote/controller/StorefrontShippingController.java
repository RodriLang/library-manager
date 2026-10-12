package com.rodrilang.librarymanager.shipping.quote.controller;

import com.rodrilang.librarymanager.shipping.quote.dto.StorefrontShippingQuoteRequest;
import com.rodrilang.librarymanager.shipping.quote.dto.StorefrontShippingQuotesResponse;
import com.rodrilang.librarymanager.shipping.quote.service.StorefrontShippingQuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/storefront/{storeId}/shipping")
@RequiredArgsConstructor
public class StorefrontShippingController {
    private final StorefrontShippingQuoteService service;

    @PostMapping("/quotes")
    public StorefrontShippingQuotesResponse quote(@PathVariable UUID storeId,
                                                  @Valid @RequestBody StorefrontShippingQuoteRequest request) {
        return service.quote(storeId, request);
    }
}
