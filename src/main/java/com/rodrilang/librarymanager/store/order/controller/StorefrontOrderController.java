package com.rodrilang.librarymanager.store.order.controller;

import com.rodrilang.librarymanager.store.order.dto.CreateStoreOrderRequest;
import com.rodrilang.librarymanager.store.order.dto.StoreOrderPublicResponse;
import com.rodrilang.librarymanager.store.order.service.StoreOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/storefront/{storeId}")
@RequiredArgsConstructor
public class StorefrontOrderController {
    private final StoreOrderService service;

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public StoreOrderPublicResponse checkout(@PathVariable UUID storeId,
                                              @Valid @RequestBody CreateStoreOrderRequest request) {
        return service.create(storeId, request);
    }

    @GetMapping("/orders/{orderNumber}")
    public StoreOrderPublicResponse track(@PathVariable UUID storeId,
                                           @PathVariable String orderNumber,
                                           @RequestParam UUID token) {
        return service.track(storeId, orderNumber, token);
    }
}
