package com.rodrilang.librarymanager.controller;

import com.rodrilang.librarymanager.dto.internal.InventoryAdvancedFilters;
import com.rodrilang.librarymanager.dto.request.AddBookToInventoryRequest;
import com.rodrilang.librarymanager.dto.request.InventoryQuantityRequest;
import com.rodrilang.librarymanager.dto.request.InventorySaleRequest;
import com.rodrilang.librarymanager.dto.request.ReactivateInventoryRequest;
import com.rodrilang.librarymanager.dto.request.UpdateInventoryRequest;
import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventoryStockSummaryResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.enums.InventoryActiveFilter;
import com.rodrilang.librarymanager.enums.InventoryPriceMode;
import com.rodrilang.librarymanager.enums.InventoryStockFilter;
import com.rodrilang.librarymanager.repository.criteria.InventorySearchCriteria;
import com.rodrilang.librarymanager.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @Deprecated
    @Operation(
            deprecated = true,
            description = "Endpoint legado. Las nuevas ventas deben registrarse mediante POST /api/sales."
    )
    @PostMapping("/{inventoryId}/sales")
    public ResponseEntity<InventoryDetailResponse> recordSale(
            @PathVariable Long inventoryId,
            @Valid @RequestBody InventorySaleRequest request
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
    public ResponseEntity<PageResponse<InventorySummaryResponse>> find(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(defaultValue = "ALL") InventoryStockFilter stock,
            @RequestParam(required = false) BookCondition condition,
            @RequestParam(required = false) List<Long> publisherIds,
            @RequestParam(required = false) List<Long> authorIds,
            @RequestParam(defaultValue = "ALL") InventoryPriceMode priceMode,
            @RequestParam(defaultValue = "ACTIVE") InventoryActiveFilter active,
            @ParameterObject
            @PageableDefault(
                    size = 30,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        InventoryAdvancedFilters filters =
                new InventoryAdvancedFilters(
                        condition,
                        publisherIds,
                        authorIds,
                        priceMode,
                        active
                );

        InventorySearchCriteria criteria =
                new InventorySearchCriteria(
                        q,
                        force,
                        stock,
                        filters
                );

        return ResponseEntity.ok(
                PageResponse.of(
                        inventoryService.find(
                                criteria,
                                pageable
                        )
                )
        );
    }

    @GetMapping("/summary")
    public ResponseEntity<InventoryStockSummaryResponse> getSummary(
            @RequestParam(required = false) BookCondition condition,
            @RequestParam(required = false) List<Long> publisherIds,
            @RequestParam(required = false) List<Long> authorIds,
            @RequestParam(defaultValue = "ALL") InventoryPriceMode priceMode,
            @RequestParam(defaultValue = "ACTIVE") InventoryActiveFilter active
    ) {
        InventoryAdvancedFilters filters =
                new InventoryAdvancedFilters(
                        condition,
                        publisherIds,
                        authorIds,
                        priceMode,
                        active
                );

        return ResponseEntity.ok(
                inventoryService.getStockSummary(filters)
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