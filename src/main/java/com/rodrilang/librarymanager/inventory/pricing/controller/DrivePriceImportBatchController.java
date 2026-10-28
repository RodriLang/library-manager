package com.rodrilang.librarymanager.inventory.pricing.controller;

import com.rodrilang.librarymanager.inventory.pricing.service.DrivePriceImportBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/inventory/price-imports/drive/batches")
@RequiredArgsConstructor
public class DrivePriceImportBatchController {
    private final DrivePriceImportBatchService batches;
    public record CreateRequest(String url, List<String> fileIds) {}
    public record UpdateRequest(String status, Long importId, String errorMessage) {}

    @PostMapping
    public DrivePriceImportBatchService.Batch create(@RequestBody CreateRequest request) {
        return batches.create(request.url(), request.fileIds());
    }
    @GetMapping("/{id}")
    public DrivePriceImportBatchService.Batch get(@PathVariable long id) { return batches.get(id); }
    @PatchMapping("/{id}/items/{itemId}")
    public DrivePriceImportBatchService.Batch update(@PathVariable long id, @PathVariable long itemId,
                                                      @RequestBody UpdateRequest request) {
        return batches.update(id, itemId, request.status(), request.importId(), request.errorMessage());
    }
}
