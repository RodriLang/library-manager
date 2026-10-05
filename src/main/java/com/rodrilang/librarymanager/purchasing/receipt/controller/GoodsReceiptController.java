package com.rodrilang.librarymanager.purchasing.receipt.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.purchasing.receipt.dto.request.*;
import com.rodrilang.librarymanager.purchasing.receipt.dto.response.GoodsReceiptDetailResponse;
import com.rodrilang.librarymanager.purchasing.receipt.dto.response.GoodsReceiptResponse;
import com.rodrilang.librarymanager.purchasing.receipt.service.GoodsReceiptService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goods-receipts")
@RequiredArgsConstructor
@Tag(name = "Recepciones de mercadería", description = "Recepción física de libros e ingreso al inventario")
public class GoodsReceiptController {
    private final GoodsReceiptService service;

    @PostMapping
    public ResponseEntity<GoodsReceiptDetailResponse> create(@Valid @RequestBody CreateGoodsReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<GoodsReceiptResponse>> findAll(
            @RequestParam(required = false) Long purchaseOrderId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(service.findAll(purchaseOrderId, pageable)));
    }

    @GetMapping("/{receiptId}")
    public ResponseEntity<GoodsReceiptDetailResponse> findById(@PathVariable Long receiptId) {
        return ResponseEntity.ok(service.findById(receiptId));
    }

    @PatchMapping("/{receiptId}")
    public ResponseEntity<GoodsReceiptDetailResponse> update(
            @PathVariable Long receiptId, @Valid @RequestBody UpdateGoodsReceiptRequest request) {
        return ResponseEntity.ok(service.update(receiptId, request));
    }

    @PostMapping("/{receiptId}/items")
    public ResponseEntity<GoodsReceiptDetailResponse> upsertItem(
            @PathVariable Long receiptId, @Valid @RequestBody UpsertGoodsReceiptItemRequest request) {
        return ResponseEntity.ok(service.upsertItem(receiptId, request));
    }

    @PostMapping("/{receiptId}/scan")
    public ResponseEntity<GoodsReceiptDetailResponse> scan(
            @PathVariable Long receiptId, @Valid @RequestBody ScanGoodsReceiptItemRequest request) {
        return ResponseEntity.ok(service.scan(receiptId, request));
    }

    @DeleteMapping("/{receiptId}/items/{itemId}")
    public ResponseEntity<GoodsReceiptDetailResponse> removeItem(@PathVariable Long receiptId, @PathVariable Long itemId) {
        return ResponseEntity.ok(service.removeItem(receiptId, itemId));
    }

    @PostMapping("/{receiptId}/confirm")
    public ResponseEntity<GoodsReceiptDetailResponse> confirm(@PathVariable Long receiptId) {
        return ResponseEntity.ok(service.confirm(receiptId));
    }

    @DeleteMapping("/{receiptId}")
    public ResponseEntity<Void> cancel(@PathVariable Long receiptId) {
        service.cancel(receiptId);
        return ResponseEntity.noContent().build();
    }
}
