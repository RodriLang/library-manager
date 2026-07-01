package com.rodrilang.librarymanager.controller;

import com.rodrilang.librarymanager.dto.request.InventoryMovementRequest;
import com.rodrilang.librarymanager.dto.request.RegisterBookPurchaseRequest;
import com.rodrilang.librarymanager.dto.request.RegisterManualBookPurchaseRequest;
import com.rodrilang.librarymanager.dto.request.PurchaseItemRequest;
import com.rodrilang.librarymanager.dto.request.RegisterPurchaseRequest;
import com.rodrilang.librarymanager.dto.request.UpdateInventoryRequest;
import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.service.InventoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Inventario", description = "Gestión obras y stock disponible en la librería ")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/purchases")
    public ResponseEntity<List<InventoryDetailResponse>> registerPurchase(
            @Valid @RequestBody RegisterPurchaseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.registerPurchase(request));
    }

    @PostMapping("/books/{bookId}/purchases")
    public ResponseEntity<InventoryDetailResponse> registerPurchaseItem(
            @PathVariable Long bookId,
            @Valid @RequestBody RegisterBookPurchaseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.registerPurchaseItem(bookId, request));
    }

    @PostMapping("/purchases/manual-book")
    public ResponseEntity<InventoryDetailResponse> registerPurchaseWithManualBook(
            @Valid @RequestBody RegisterManualBookPurchaseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.registerPurchaseWithManualBook(request));
    }

    @PostMapping("/books/{bookId}/sales")
    public ResponseEntity<InventoryDetailResponse> registerSale(
            @PathVariable Long bookId,
            @Valid @RequestBody InventoryMovementRequest request) {
        return ResponseEntity.ok(inventoryService.registerSale(bookId, request));
    }

    @PostMapping("/books/{bookId}/returns")
    public ResponseEntity<InventoryDetailResponse> registerReturn(
            @PathVariable Long bookId,
            @Valid @RequestBody InventoryMovementRequest request) {
        return ResponseEntity.ok(inventoryService.registerReturn(bookId, request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<InventorySummaryResponse>> getAll(
            @ParameterObject
            @PageableDefault(size = 20)
            Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.of(inventoryService.getAll(pageable)));
    }

    @GetMapping("/{bookId}")
    public ResponseEntity<InventoryDetailResponse> getById(@PathVariable Long bookId) {
        return ResponseEntity.ok(inventoryService.getByBookId(bookId));
    }

    @GetMapping("/search")
    public ResponseEntity<PageResponse<InventorySummaryResponse>> search(
            @RequestParam String q,
            @ParameterObject
            @PageableDefault(size = 20)
            Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.of(inventoryService.search(q, pageable)));
    }

    @PutMapping("/books/{bookId}")
    public ResponseEntity<InventoryDetailResponse> updateByBookId(
            @PathVariable Long bookId,
            @Valid @RequestBody UpdateInventoryRequest request
    ) {
        return ResponseEntity.ok(inventoryService.updateByBookId(bookId, request));
    }
}
