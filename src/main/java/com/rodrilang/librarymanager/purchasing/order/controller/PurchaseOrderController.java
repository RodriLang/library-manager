package com.rodrilang.librarymanager.purchasing.order.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.purchasing.order.dto.PurchaseOrderFilter;
import com.rodrilang.librarymanager.purchasing.order.dto.request.AddPurchaseOrderItemRequest;
import com.rodrilang.librarymanager.purchasing.order.dto.request.CreatePurchaseOrderRequest;
import com.rodrilang.librarymanager.purchasing.order.dto.request.CreatePurchaseOrdersFromRequirementsRequest;
import com.rodrilang.librarymanager.purchasing.order.dto.request.UpdatePurchaseOrderItemRequest;
import com.rodrilang.librarymanager.purchasing.order.dto.response.CreatePurchaseOrdersFromRequirementsResponse;
import com.rodrilang.librarymanager.purchasing.order.dto.response.PurchaseOrderDetailResponse;
import com.rodrilang.librarymanager.purchasing.order.dto.response.PurchaseOrderResponse;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderStatus;
import com.rodrilang.librarymanager.purchasing.order.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
@Tag(name = "Pedidos de compra", description = "Gestión de pedidos a proveedores")
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    @PostMapping
    public ResponseEntity<PurchaseOrderDetailResponse> create(
            @Valid
            @RequestBody
            CreatePurchaseOrderRequest request
    ) {

        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/from-requirements")
    public ResponseEntity<CreatePurchaseOrdersFromRequirementsResponse> createFromRequirements(
            @Valid
            @RequestBody CreatePurchaseOrdersFromRequirementsRequest request
    ) {

        return ResponseEntity.status(HttpStatus.CREATED).body(service.createFromRequirements(request)
        );
    }

    @GetMapping
    public ResponseEntity<PageResponse<PurchaseOrderResponse>> findAll(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @ParameterObject
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {

        PurchaseOrderFilter filter = new PurchaseOrderFilter(query, providerId, status);

        return ResponseEntity.ok(PageResponse.of(service.findAll(filter, pageable)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<PurchaseOrderDetailResponse> findById(
            @PathVariable Long orderId
    ) {

        return ResponseEntity.ok(service.findById(orderId));
    }

    @PostMapping("/{orderId}/items")
    public ResponseEntity<PurchaseOrderDetailResponse> addItem(
            @PathVariable Long orderId,
            @Valid
            @RequestBody
            AddPurchaseOrderItemRequest request
    ) {

        return ResponseEntity.ok(service.addItem(orderId, request));
    }

    @PatchMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<PurchaseOrderDetailResponse> updateItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @Valid
            @RequestBody
            UpdatePurchaseOrderItemRequest request
    ) {

        return ResponseEntity.ok(service.updateItem(orderId, itemId, request));
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<PurchaseOrderDetailResponse> removeItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId
    ) {

        return ResponseEntity.ok(service.removeItem(orderId, itemId));
    }

    @PostMapping("/{orderId}/send")
    public ResponseEntity<PurchaseOrderDetailResponse> send(
            @PathVariable Long orderId
    ) {

        return ResponseEntity.ok(service.send(orderId));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancel(
            @PathVariable Long orderId
    ) {

        service.cancel(orderId);

        return ResponseEntity.noContent().build();
    }
}