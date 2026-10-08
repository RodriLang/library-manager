package com.rodrilang.librarymanager.store.order.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.store.order.dto.*;
import com.rodrilang.librarymanager.store.order.model.*;
import com.rodrilang.librarymanager.store.order.service.StoreOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store/orders")
@RequiredArgsConstructor
public class StoreOrderAdminController {
    private final StoreOrderService service;

    @GetMapping
    public PageResponse<StoreOrderSummaryResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StoreOrderStatus status,
            @RequestParam(required = false) StorePaymentStatus paymentStatus,
            @RequestParam(required = false) StoreFulfillmentStatus fulfillmentStatus,
            Pageable pageable) {
        return service.adminList(q, status, paymentStatus, fulfillmentStatus, pageable);
    }

    @GetMapping("/{orderId}")
    public StoreOrderResponse detail(@PathVariable Long orderId) {
        return service.adminDetail(orderId);
    }

    @PostMapping("/{orderId}/confirm")
    public StoreOrderResponse confirm(@PathVariable Long orderId) {
        return service.confirm(orderId);
    }

    @PostMapping("/{orderId}/cancel")
    public StoreOrderResponse cancel(@PathVariable Long orderId,
                                     @Valid @RequestBody(required = false) CancelStoreOrderRequest request) {
        return service.cancel(orderId, request);
    }

    @PatchMapping("/{orderId}/fulfillment")
    public StoreOrderResponse updateFulfillment(@PathVariable Long orderId,
                                                @Valid @RequestBody UpdateStoreFulfillmentRequest request) {
        return service.updateFulfillment(orderId, request);
    }

    @PostMapping("/{orderId}/complete")
    public StoreOrderResponse complete(@PathVariable Long orderId,
                                       @Valid @RequestBody CompleteStoreOrderRequest request) {
        return service.complete(orderId, request);
    }
}
