package com.rodrilang.librarymanager.inventory.valuation.controller;

import com.rodrilang.librarymanager.inventory.valuation.dto.response.InventoryValuationResponse;
import com.rodrilang.librarymanager.inventory.valuation.service.InventoryValuationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports/inventory-valuation")
@RequiredArgsConstructor
@Tag(name = "Reportes - Valuación de inventario", description = "Valuación del stock actual a PVP, costo histórico y costo de reposición")
public class InventoryValuationController {

    private final InventoryValuationService service;

    @GetMapping
    public ResponseEntity<InventoryValuationResponse> getCurrent() {
        return ResponseEntity.ok(service.getCurrent());
    }
}
