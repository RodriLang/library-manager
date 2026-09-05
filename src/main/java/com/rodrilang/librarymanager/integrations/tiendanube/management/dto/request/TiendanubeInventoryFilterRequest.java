package com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request;

import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeAdminPublicationFilter;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeAdminStockFilter;

public record TiendanubeInventoryFilterRequest(
        String q,
        TiendanubeAdminPublicationFilter publication,
        Long publisherId,
        TiendanubeAdminStockFilter stock,
        Boolean priceSyncEnabled
) {

    public TiendanubeInventoryFilterRequest {
        publication = publication != null ? publication : TiendanubeAdminPublicationFilter.ALL;
        stock = stock != null ? stock : TiendanubeAdminStockFilter.ALL;
        q = q == null || q.isBlank() ? null : q.trim();
    }

    public static TiendanubeInventoryFilterRequest empty() {
        return new TiendanubeInventoryFilterRequest(null, null, null, null, null);
    }
}
