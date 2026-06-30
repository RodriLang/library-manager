package com.rodrilang.librarymanager.controller;

import com.rodrilang.librarymanager.dto.request.RegisterManualBookPurchaseRequest;
import com.rodrilang.librarymanager.dto.request.RegisterPurchaseItemRequest;
import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/purchases")
    public ResponseEntity<List<InventoryDetailResponse>> registerPurchase(
            @RequestBody List<RegisterPurchaseItemRequest> request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.registerPurchase(request));
    }

    @PostMapping("/purchases/manual-book")
    public ResponseEntity<InventoryDetailResponse> registerPurchaseWithManualBook(
            @RequestBody RegisterManualBookPurchaseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.registerPurchaseWithManualBook(request));
    }
}
