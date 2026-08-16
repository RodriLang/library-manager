package com.rodrilang.librarymanager.inventory.movement.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryMovementFilter;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryMovementResponse;
import com.rodrilang.librarymanager.inventory.movement.service.InventoryMovementQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Movimientos de inventario", description = "Consulta y auditoría de los movimientos de stock del inventario")
@RestController
@RequestMapping("/api/inventory/movements")
@RequiredArgsConstructor
public class InventoryMovementController {

    private final InventoryMovementQueryService service;

    @GetMapping
    public ResponseEntity<PageResponse<InventoryMovementResponse>> findAll(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long inventoryId,
            @RequestParam(required = false) InventoryMovementType type,
            @RequestParam(required = false) InventoryMovementSource source,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @ParameterObject
            @PageableDefault(
                    size = 25,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {

        InventoryMovementFilter filter = new InventoryMovementFilter(
                query,
                inventoryId,
                type,
                source,
                from,
                to
        );

        return ResponseEntity.ok(PageResponse.of(service.findAll(filter, pageable)));
    }
}