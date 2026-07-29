package com.rodrilang.librarymanager.integrations.tiendanube.dto.request;

public record TiendanubeUpdateVariantRequest(
        String sku,
        String barcode,
        Integer stock,
        Boolean stockManagement
) {
}
