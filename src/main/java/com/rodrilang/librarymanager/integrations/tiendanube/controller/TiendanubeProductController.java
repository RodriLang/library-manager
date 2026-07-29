package com.rodrilang.librarymanager.integrations.tiendanube.controller;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.LinkTiendanubeProductRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductLinkResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubePublishResultResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeRemoteProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/integrations/tiendanube")
@RequiredArgsConstructor
public class TiendanubeProductController {

    private final TiendanubeProductService productService;

    @PostMapping("/inventories/{inventoryId}/publish")
    @ResponseStatus(HttpStatus.CREATED)
    public TiendanubePublishResultResponse publishInventory(@PathVariable Long inventoryId) {
        return productService.publishInventory(inventoryId);
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