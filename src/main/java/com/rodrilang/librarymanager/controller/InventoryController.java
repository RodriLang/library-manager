package com.rodrilang.librarymanager.controller;

import com.rodrilang.librarymanager.dto.request.AddBookToInventoryRequest;
import com.rodrilang.librarymanager.dto.request.InventoryQuantityRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventario", description = "Gestión del inventario de la librería")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/books/{bookId}")
    public ResponseEntity<InventoryDetailResponse> addBook(
            @PathVariable Long bookId,
            @Valid @RequestBody AddBookToInventoryRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(inventoryService.addToInventory(bookId, request));
    }

    @PostMapping("/books/{bookId}/entries")
    public ResponseEntity<InventoryDetailResponse> recordStockEntry(
            @PathVariable Long bookId,
            @Valid @RequestBody InventoryQuantityRequest request
    ) {
        return ResponseEntity.ok(
                inventoryService.addStock(bookId, request)
        );
    }

    @PostMapping("/books/{bookId}/sales")
    public ResponseEntity<InventoryDetailResponse> recordSale(
            @PathVariable Long bookId,
            @Valid @RequestBody InventoryQuantityRequest request
    ) {
        return ResponseEntity.ok(
                inventoryService.recordSale(bookId, request)
        );
    }

    @GetMapping
    public ResponseEntity<PageResponse<InventorySummaryResponse>> getAll(
            @ParameterObject
            @PageableDefault(size = 20)
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                PageResponse.of(inventoryService.getAll(pageable))
        );
    }

    @GetMapping("/{bookId}")
    public ResponseEntity<InventoryDetailResponse> getByBookId(
            @PathVariable Long bookId
    ) {
        return ResponseEntity.ok(
                inventoryService.getByBookId(bookId)
        );
    }

    @GetMapping("/search")
    public ResponseEntity<PageResponse<InventorySummaryResponse>> search(
            @RequestParam String q,
            @ParameterObject
            @PageableDefault(size = 20)
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                PageResponse.of(inventoryService.search(q.trim(), pageable))
        );
    }

    @PutMapping("/books/{bookId}")
    public ResponseEntity<InventoryDetailResponse> update(
            @PathVariable Long bookId,
            @Valid @RequestBody UpdateInventoryRequest request
    ) {
        return ResponseEntity.ok(
                inventoryService.update(bookId, request)
        );
    }

    @DeleteMapping("/books/{bookId}")
    public ResponseEntity<Void> removeBook(
            @PathVariable Long bookId
    ) {
        inventoryService.removeBook(bookId);
        return ResponseEntity.noContent().build();
    }
}