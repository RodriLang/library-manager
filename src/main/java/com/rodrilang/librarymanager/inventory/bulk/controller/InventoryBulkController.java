package com.rodrilang.librarymanager.inventory.bulk.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.enums.InventoryStockFilter;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkActionRequest;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkFilterRequest;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkSelectionRequest;
import com.rodrilang.librarymanager.inventory.bulk.dto.response.InventoryBulkActionResponse;
import com.rodrilang.librarymanager.inventory.bulk.dto.response.InventoryBulkItemResponse;
import com.rodrilang.librarymanager.inventory.bulk.dto.response.InventoryBulkPreviewResponse;
import com.rodrilang.librarymanager.inventory.bulk.service.InventoryBulkQueryService;
import com.rodrilang.librarymanager.inventory.bulk.service.InventoryBulkService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/bulk")
@RequiredArgsConstructor
@Tag(
        name = "Gestión masiva de inventario",
        description = "Consulta, selección y acciones masivas sobre el inventario"
)
public class InventoryBulkController {

    private final InventoryBulkQueryService queryService;
    private final InventoryBulkService bulkService;

    @GetMapping("/items")
    public ResponseEntity<PageResponse<InventoryBulkItemResponse>> find(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BookCondition condition,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long publisherId,
            @RequestParam(defaultValue = "ALL") InventoryStockFilter stock,
            @ParameterObject
            @PageableDefault(
                    size = 50,
                    sort = "title",
                    direction = Sort.Direction.ASC
            )
            Pageable pageable
    ) {
        InventoryBulkFilterRequest filter =
                new InventoryBulkFilterRequest(
                        q,
                        condition,
                        active,
                        publisherId,
                        stock
                );

        return ResponseEntity.ok(
                PageResponse.of(
                        queryService.find(
                                filter,
                                pageable
                        )
                )
        );
    }

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