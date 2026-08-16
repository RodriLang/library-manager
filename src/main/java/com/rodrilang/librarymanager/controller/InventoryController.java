package com.rodrilang.librarymanager.controller;

import com.rodrilang.librarymanager.dto.request.AddBookToInventoryRequest;
import com.rodrilang.librarymanager.dto.request.InventoryQuantityRequest;
import com.rodrilang.librarymanager.dto.request.ReactivateInventoryRequest;
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

    @PostMapping("/{inventoryId}/entries")
    public ResponseEntity<InventoryDetailResponse> recordStockEntry(
            @PathVariable Long inventoryId,
            @Valid @RequestBody InventoryQuantityRequest request
    ) {
        return ResponseEntity.ok(
                inventoryService.addStock(inventoryId, request)
        );
    }

    @PostMapping("/{inventoryId}/sales")
    public ResponseEntity<InventoryDetailResponse> recordSale(
            @PathVariable Long inventoryId,
            @Valid @RequestBody InventoryQuantityRequest request
    ) {
        return ResponseEntity.ok(
                inventoryService.recordSale(inventoryId, request)
        );
    }

    @PostMapping("/{inventoryId}/reactivate")
    public InventoryDetailResponse reactivate(
            @PathVariable Long inventoryId,
            @Valid @RequestBody ReactivateInventoryRequest request
    ) {
        return inventoryService.reactivate(inventoryId, request);
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

    @GetMapping("/{inventoryId}")
    public ResponseEntity<InventoryDetailResponse> getById(
            @PathVariable Long inventoryId
    ) {
        return ResponseEntity.ok(
                inventoryService.getById(inventoryId)
        );
    }

    @GetMapping("/by-book/{bookId}")
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
            @RequestParam(defaultValue = "false") boolean force,
            @ParameterObject
            @PageableDefault(size = 20)
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                PageResponse.of(inventoryService.search(q.trim(), force, pageable))
        );
    }

    @PutMapping("/{inventoryId}")
    public ResponseEntity<InventoryDetailResponse> update(
            @PathVariable Long inventoryId,
            @Valid @RequestBody UpdateInventoryRequest request
    ) {
        return ResponseEntity.ok(
                inventoryService.update(inventoryId, request)
        );
    }

    @DeleteMapping("/{inventoryId}")
    public ResponseEntity<Void> deactivate(
            @PathVariable Long inventoryId
    ) {
        inventoryService.deactivate(inventoryId);
        return ResponseEntity.noContent().build();
    }
}