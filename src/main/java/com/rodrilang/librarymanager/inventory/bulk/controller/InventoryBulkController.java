package com.rodrilang.librarymanager.inventory.bulk.controller;

import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkActionRequest;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkSelectionRequest;
import com.rodrilang.librarymanager.inventory.bulk.dto.response.InventoryBulkActionResponse;
import com.rodrilang.librarymanager.inventory.bulk.dto.response.InventoryBulkPreviewResponse;
import com.rodrilang.librarymanager.inventory.bulk.service.InventoryBulkQueryService;
import com.rodrilang.librarymanager.inventory.bulk.service.InventoryBulkService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/bulk")
@RequiredArgsConstructor
@Tag(name = "Gestión masiva de inventario", description = "Previsualización y acciones masivas sobre selecciones del inventario")
public class InventoryBulkController {

    private final InventoryBulkQueryService queryService;
    private final InventoryBulkService bulkService;

    @PostMapping("/preview")
    public ResponseEntity<InventoryBulkPreviewResponse> preview(
            @Valid
            @RequestBody
            InventoryBulkSelectionRequest request
    ) {
        return ResponseEntity.ok(
                queryService.preview(request)
        );
    }

    @PostMapping("/actions")
    public ResponseEntity<InventoryBulkActionResponse> execute(
            @Valid
            @RequestBody
            InventoryBulkActionRequest request
    ) {
        return ResponseEntity.ok(
                bulkService.execute(request)
        );
    }
}