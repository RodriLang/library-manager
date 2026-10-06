package com.rodrilang.librarymanager.inventory.consignment.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.inventory.consignment.dto.*;
import com.rodrilang.librarymanager.inventory.consignment.service.ConsignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/consignments")
@RequiredArgsConstructor
public class ConsignmentController {
    private final ConsignmentService service;

    @GetMapping("/inventory")
    public PageResponse<ConsignmentInventoryResponse> inventory(@PageableDefault(size = 30) Pageable pageable) {
        return PageResponse.of(service.inventory(pageable));
    }

    @GetMapping("/sales")
    public PageResponse<ConsignmentSaleResponse> sales(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) Boolean settled,
            @PageableDefault(size = 30) Pageable pageable
    ) {
        return PageResponse.of(service.sales(providerId, settled, pageable));
    }

    @PostMapping("/settlements")
    public ResponseEntity<ConsignmentSettlementResponse> settle(@Valid @RequestBody CreateConsignmentSettlementRequest request) {
        return ResponseEntity.ok(service.settle(request));
    }

    @PostMapping("/settlements/{settlementId}/cancel")
    public ResponseEntity<ConsignmentSettlementResponse> cancelSettlement(@PathVariable Long settlementId) {
        return ResponseEntity.ok(service.cancelSettlement(settlementId));
    }

    @GetMapping("/settlements")
    public PageResponse<ConsignmentSettlementResponse> settlements(@PageableDefault(size = 30) Pageable pageable) {
        return PageResponse.of(service.settlements(pageable));
    }
}
