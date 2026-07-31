package com.rodrilang.librarymanager.integrations.tiendanube.controller;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.LinkTiendanubeProductRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeInventoryStatusResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductLinkResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubePublishResultResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeRemoteProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeRetryResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeVariantSyncService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Tiendanube - Productos", description = "Publicación, vinculación y sincronización de productos con Tiendanube")
@RestController
@RequestMapping("/api/integrations/tiendanube")
@RequiredArgsConstructor
public class TiendanubeProductController {

    private final TiendanubeProductService productService;
    private final TiendanubeVariantSyncService variantSyncService;

    @PostMapping("/inventories/{inventoryId}/publish")
    @ResponseStatus(HttpStatus.CREATED)
    public TiendanubePublishResultResponse publishInventory(@PathVariable Long inventoryId) {
        return productService.publishInventory(inventoryId);
    }

    @PostMapping("/inventories/{inventoryId}/sync")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void syncInventory(@PathVariable Long inventoryId) {
        variantSyncService.syncVariant(inventoryId);
    }

    @PostMapping("/inventories/{inventoryId}/retry")
    public TiendanubeRetryResponse retryInventory(@PathVariable Long inventoryId) {
        return productService.retry(inventoryId);
    }

    @GetMapping("/inventories/{inventoryId}/status")
    public TiendanubeInventoryStatusResponse getInventoryStatus(@PathVariable Long inventoryId) {
        return productService.getInventoryStatus(inventoryId);
    }

    @GetMapping("/products")
    public List<TiendanubeRemoteProductResponse> getRemoteProducts(@RequestParam Long bookstoreId) {
        // TODO remover bookstoreId cuando haya autenticación
        return productService.getRemoteProducts(bookstoreId);
    }

    @PostMapping("/products/link")
    public TiendanubeProductLinkResponse linkExistingProduct(
            @Valid @RequestBody LinkTiendanubeProductRequest request
    ) {
        return productService.linkExistingProduct(
                request.inventoryId(),
                request.productId(),
                request.variantId()
        );
    }
}