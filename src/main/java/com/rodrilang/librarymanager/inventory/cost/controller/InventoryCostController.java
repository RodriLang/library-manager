package com.rodrilang.librarymanager.inventory.cost.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.inventory.cost.dto.request.BulkEstimateInventoryCostRequest;
import com.rodrilang.librarymanager.inventory.cost.dto.request.BulkSetInventoryDiscountRequest;
import com.rodrilang.librarymanager.inventory.cost.dto.request.SetInventoryCostRequest;
import com.rodrilang.librarymanager.inventory.cost.dto.response.BulkInventoryCostUpdateResponse;
import com.rodrilang.librarymanager.inventory.cost.dto.response.InventoryCostLayerResponse;
import com.rodrilang.librarymanager.inventory.cost.dto.response.InventoryCostSummaryResponse;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostType;
import com.rodrilang.librarymanager.inventory.cost.service.InventoryCostCommandService;
import com.rodrilang.librarymanager.inventory.cost.service.InventoryCostQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory-costs")
@RequiredArgsConstructor
@Tag(name = "Costos de inventario", description = "Capas de costo e información económica pendiente del inventario")
public class InventoryCostController {

    private final InventoryCostQueryService queryService;
    private final InventoryCostCommandService commandService;

    @GetMapping
    public ResponseEntity<PageResponse<InventoryCostLayerResponse>> findAll(
            @RequestParam(required = false) InventoryCostType costType,
            @RequestParam(required = false) Boolean missingDiscount,
            @RequestParam(required = false) Boolean remainingOnly,
            @RequestParam(required = false) String q,
            @ParameterObject
            @PageableDefault(size = 50, sort = "enteredAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.of(
                queryService.findAll(costType, missingDiscount, remainingOnly, q, pageable)
        ));
    }

    @GetMapping("/summary")
    public ResponseEntity<InventoryCostSummaryResponse> summary() {
        return ResponseEntity.ok(queryService.summary());
    }

    @PatchMapping("/{layerId}")
    public ResponseEntity<InventoryCostLayerResponse> setCost(
            @PathVariable Long layerId,
            @Valid @RequestBody SetInventoryCostRequest request
    ) {
        return ResponseEntity.ok(commandService.setCost(layerId, request));
    }


    @PostMapping("/bulk/discount")
    public ResponseEntity<BulkInventoryCostUpdateResponse> setDiscount(
            @Valid @RequestBody BulkSetInventoryDiscountRequest request
    ) {
        return ResponseEntity.ok(commandService.setDiscount(request));
    }

    @PostMapping("/bulk/estimate")
    public ResponseEntity<BulkInventoryCostUpdateResponse> estimateByDiscount(
            @Valid @RequestBody BulkEstimateInventoryCostRequest request
    ) {
        return ResponseEntity.ok(commandService.estimateByDiscount(request));
    }
}
