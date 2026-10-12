package com.rodrilang.librarymanager.shipping.quote.controller;

import com.rodrilang.librarymanager.shipping.quote.dto.StorefrontShippingQuoteRequest;
import com.rodrilang.librarymanager.shipping.quote.dto.StorefrontShippingQuotesResponse;
import com.rodrilang.librarymanager.shipping.quote.service.StorefrontShippingQuoteService;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.operations.ShippingPickupPoint;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/storefront/{storeId}/shipping")
@RequiredArgsConstructor
public class StorefrontShippingController {
    private final StorefrontShippingQuoteService service;


    @GetMapping("/pickup-points")
    public List<ShippingPickupPoint> pickupPoints(@PathVariable UUID storeId,
                                                  @RequestParam ShippingProviderType provider,
                                                  @RequestParam String postalCode) {
        return service.pickupPoints(storeId, provider, postalCode);
    }

    @PostMapping("/quotes")
    public StorefrontShippingQuotesResponse quote(@PathVariable UUID storeId,
                                                  @Valid @RequestBody StorefrontShippingQuoteRequest request) {
        return service.quote(storeId, request);
    }
}
