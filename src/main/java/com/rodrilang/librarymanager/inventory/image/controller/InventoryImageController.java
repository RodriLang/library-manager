package com.rodrilang.librarymanager.inventory.image.controller;

import com.rodrilang.librarymanager.inventory.image.dto.*;
import com.rodrilang.librarymanager.inventory.image.service.InventoryImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/inventory/{inventoryId}/images")
@RequiredArgsConstructor
public class InventoryImageController {
    private final InventoryImageService service;

    @GetMapping
    public InventoryImageGalleryResponse gallery(@PathVariable Long inventoryId) {
        return service.getGallery(inventoryId);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InventoryImageGalleryResponse> upload(
            @PathVariable Long inventoryId,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(inventoryId, file));
    }

    @PostMapping("/url")
    public ResponseEntity<InventoryImageGalleryResponse> addUrl(
            @PathVariable Long inventoryId,
            @Valid @RequestBody AddInventoryImageUrlRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addUrl(inventoryId, request.url()));
    }

    @PutMapping("/{imageId}/primary")
    public InventoryImageGalleryResponse makePrimary(@PathVariable Long inventoryId, @PathVariable Long imageId) {
        return service.makePrimary(inventoryId, imageId);
    }

    @PutMapping("/book-cover/primary")
    public InventoryImageGalleryResponse useBookCoverAsPrimary(@PathVariable Long inventoryId) {
        return service.useBookCoverAsPrimary(inventoryId);
    }

    @PutMapping("/order")
    public InventoryImageGalleryResponse reorder(
            @PathVariable Long inventoryId,
            @Valid @RequestBody ReorderInventoryImagesRequest request
    ) {
        return service.reorder(inventoryId, request.imageIds());
    }

    @DeleteMapping("/{imageId}")
    public InventoryImageGalleryResponse delete(@PathVariable Long inventoryId, @PathVariable Long imageId) {
        return service.delete(inventoryId, imageId);
    }
}
