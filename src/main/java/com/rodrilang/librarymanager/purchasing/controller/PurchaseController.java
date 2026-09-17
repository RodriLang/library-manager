package com.rodrilang.librarymanager.purchasing.controller;

import com.rodrilang.librarymanager.purchasing.dto.request.CreatePurchaseRequest;
import com.rodrilang.librarymanager.purchasing.dto.request.UpsertPurchaseItemRequest;
import com.rodrilang.librarymanager.purchasing.dto.request.ScanPurchaseItemRequest;
import com.rodrilang.librarymanager.purchasing.dto.response.PurchaseResponse;
import com.rodrilang.librarymanager.purchasing.service.PurchaseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
@Tag(name = "Compras", description = "Compras de libros a proveedores")
public class PurchaseController {
    private final PurchaseService purchaseService;

    @GetMapping
    public ResponseEntity<List<PurchaseResponse>> findAll() {
        return ResponseEntity.ok(purchaseService.findAll());
    }

    @GetMapping("/{purchaseId}")
    public ResponseEntity<PurchaseResponse> findById(@PathVariable Long purchaseId) {
        return ResponseEntity.ok(purchaseService.findById(purchaseId));
    }

    @PostMapping
    public ResponseEntity<PurchaseResponse> create(@Valid @RequestBody CreatePurchaseRequest request) {
        return ResponseEntity.ok(purchaseService.create(request));
    }

    @PostMapping("/{purchaseId}/items/scan")
    public ResponseEntity<PurchaseResponse> scanItem(
            @PathVariable Long purchaseId,
            @Valid @RequestBody ScanPurchaseItemRequest request
    ) {
        return ResponseEntity.ok(purchaseService.scanItem(purchaseId, request));
    }

    @PutMapping("/{purchaseId}/items")
    public ResponseEntity<PurchaseResponse> upsertItem(
            @PathVariable Long purchaseId,
            @Valid @RequestBody UpsertPurchaseItemRequest request
    ) {
        return ResponseEntity.ok(purchaseService.upsertItem(purchaseId, request));
    }

    @DeleteMapping("/{purchaseId}/items/{itemId}")
    public ResponseEntity<PurchaseResponse> removeItem(@PathVariable Long purchaseId, @PathVariable Long itemId) {
        return ResponseEntity.ok(purchaseService.removeItem(purchaseId, itemId));
    }

    @PostMapping("/{purchaseId}/confirm")
    public ResponseEntity<PurchaseResponse> confirm(@PathVariable Long purchaseId) {
        return ResponseEntity.ok(purchaseService.confirm(purchaseId));
    }
}
