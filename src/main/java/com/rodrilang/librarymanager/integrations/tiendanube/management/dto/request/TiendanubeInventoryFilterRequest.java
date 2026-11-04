package com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request;

import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeAdminPublicationFilter;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeAdminStockFilter;

public record TiendanubeInventoryFilterRequest(
        String q,
        TiendanubeAdminPublicationFilter publication,
        Long publisherId,
        TiendanubeAdminStockFilter stock,
        Boolean priceSyncEnabled,
        String categorySyncStatus
) {

    public TiendanubeInventoryFilterRequest {
        publication = publication != null ? publication : TiendanubeAdminPublicationFilter.ALL;
        stock = stock != null ? stock : TiendanubeAdminStockFilter.ALL;
        q = q == null || q.isBlank() ? null : q.trim();
        categorySyncStatus = categorySyncStatus == null || categorySyncStatus.isBlank() || "ALL".equals(categorySyncStatus)
                ? null : categorySyncStatus.toUpperCase(java.util.Locale.ROOT);
        if (categorySyncStatus != null && !java.util.Set.of("SYNCED", "PENDING", "ERROR", "UNVERIFIED").contains(categorySyncStatus)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Estado de categorías inválido");
        }
    }

    public static TiendanubeInventoryFilterRequest empty() {
        return new TiendanubeInventoryFilterRequest(null, null, null, null, null, null);
    }
}
