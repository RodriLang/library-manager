package com.rodrilang.librarymanager.integrations.tiendanube.controller;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeBulkImportRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeBulkImportResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImportPreviewResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImportResultResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeImportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Tiendanube - Importación",
        description = "Previsualización e importación de publicaciones de Tiendanube al inventario"
)
@RestController
@RequestMapping("/api/integrations/tiendanube/import")
@RequiredArgsConstructor
public class TiendanubeImportController {

    private final TiendanubeImportService importService;

    @GetMapping("/preview")
    public TiendanubeImportPreviewResponse preview(@RequestParam Long bookstoreId) {
        return importService.preview(bookstoreId);
    }

    @PostMapping("/products/{productId}/variants/{variantId}")
    @ResponseStatus(HttpStatus.CREATED)
    public TiendanubeImportResultResponse importProduct(
            @RequestParam Long bookstoreId,
            @PathVariable Long productId,
            @PathVariable Long variantId
    ) {
        return importService.importProduct(bookstoreId, productId, variantId);
    }

    @PostMapping
    public TiendanubeBulkImportResponse importProducts(
            @RequestParam Long bookstoreId,
            @Valid @RequestBody TiendanubeBulkImportRequest request
    ) {
        return importService.importProducts(bookstoreId, request);
    }
}