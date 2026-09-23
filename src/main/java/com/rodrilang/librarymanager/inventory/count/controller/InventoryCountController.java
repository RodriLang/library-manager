package com.rodrilang.librarymanager.inventory.count.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.inventory.count.dto.request.AddInventoryCountBookRequest;
import com.rodrilang.librarymanager.inventory.count.dto.request.ApplyInventoryCountRequest;
import com.rodrilang.librarymanager.inventory.count.dto.request.CreateInventoryCountRequest;
import com.rodrilang.librarymanager.inventory.count.dto.request.ScanInventoryCountRequest;
import com.rodrilang.librarymanager.inventory.count.dto.request.UpdateInventoryCountConfigurationRequest;
import com.rodrilang.librarymanager.inventory.count.dto.request.UpdateInventoryCountItemRequest;
import com.rodrilang.librarymanager.inventory.count.dto.response.InventoryCountItemResponse;
import com.rodrilang.librarymanager.inventory.count.dto.response.InventoryCountReportSummaryResponse;
import com.rodrilang.librarymanager.inventory.count.dto.response.InventoryCountResultResponse;
import com.rodrilang.librarymanager.inventory.count.dto.response.InventoryCountSessionResponse;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountDifferenceType;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.service.InventoryCountItemService;
import com.rodrilang.librarymanager.inventory.count.service.InventoryCountService;
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
@RequestMapping("/api/inventory-counts")
@RequiredArgsConstructor
@Tag(name = "Conteos de inventario", description = "Carga rápida, reemplazo y auditoría física de inventario")
public class InventoryCountController {

    private final InventoryCountService service;
    private final InventoryCountItemService itemService;

    @PostMapping
    public ResponseEntity<InventoryCountSessionResponse> create(@Valid @RequestBody CreateInventoryCountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{sessionId}/items")
    public ResponseEntity<InventoryCountItemResponse> addBook(
            @PathVariable Long sessionId,
            @Valid @RequestBody AddInventoryCountBookRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(itemService.addBook(sessionId, request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<InventoryCountSessionResponse>> findAll(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.of(service.findAll(pageable)));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<InventoryCountSessionResponse> findById(@PathVariable Long sessionId) {
        return ResponseEntity.ok(service.findById(sessionId));
    }

    @GetMapping("/active-absolute")
    public ResponseEntity<InventoryCountSessionResponse> findActiveAbsolute(
            @RequestParam BookCondition condition
    ) {
        return service
                .findActiveAbsolute(condition)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{sessionId}/items")
    public ResponseEntity<PageResponse<InventoryCountItemResponse>> findItems(
            @PathVariable Long sessionId,
            @RequestParam(required = false) InventoryCountItemStatus status,
            @RequestParam(required = false) String q,
            @ParameterObject
            @PageableDefault(size = 50, sort = "lastScannedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.of(service.findItems(sessionId, status, q, pageable)));
    }

    @PostMapping("/{sessionId}/scan")
    public ResponseEntity<InventoryCountItemResponse> scan(
            @PathVariable Long sessionId,
            @Valid @RequestBody ScanInventoryCountRequest request
    ) {
        return ResponseEntity.ok(itemService.scan(sessionId, request));
    }

    @PatchMapping("/{sessionId}/items/{itemId}")
    public ResponseEntity<InventoryCountItemResponse> updateItem(
            @PathVariable Long sessionId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateInventoryCountItemRequest request
    ) {
        return ResponseEntity.ok(itemService.update(sessionId, itemId, request));
    }

    @DeleteMapping("/{sessionId}/items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long sessionId, @PathVariable Long itemId) {
        itemService.delete(sessionId, itemId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{sessionId}/configuration")
    public ResponseEntity<InventoryCountSessionResponse> updateConfiguration(
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateInventoryCountConfigurationRequest request
    ) {
        return ResponseEntity.ok(service.updateConfiguration(sessionId, request));
    }

    @PostMapping("/{sessionId}/review")
    public ResponseEntity<InventoryCountSessionResponse> review(@PathVariable Long sessionId) {
        return ResponseEntity.ok(service.review(sessionId));
    }

    @GetMapping("/{sessionId}/report")
    public ResponseEntity<InventoryCountReportSummaryResponse> report(@PathVariable Long sessionId) {
        return ResponseEntity.ok(service.report(sessionId));
    }

    @GetMapping("/{sessionId}/results")
    public ResponseEntity<PageResponse<InventoryCountResultResponse>> findResults(
            @PathVariable Long sessionId,
            @RequestParam(required = false) InventoryCountDifferenceType difference,
            @RequestParam(required = false) String q,
            @ParameterObject
            @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.of(service.findResults(sessionId, difference, q, pageable)));
    }

    @PostMapping("/{sessionId}/apply")
    public ResponseEntity<InventoryCountSessionResponse> apply(
            @PathVariable Long sessionId,
            @RequestBody(required = false) ApplyInventoryCountRequest request
    ) {
        boolean allowConcurrentMovements = request != null && request.allowConcurrentMovements();
        return ResponseEntity.ok(service.apply(sessionId, allowConcurrentMovements));
    }

    @PostMapping("/{sessionId}/revert")
    public ResponseEntity<InventoryCountSessionResponse> revert(@PathVariable Long sessionId) {
        return ResponseEntity.ok(service.revert(sessionId));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> cancel(@PathVariable Long sessionId) {
        service.cancel(sessionId);
        return ResponseEntity.noContent().build();
    }
}
