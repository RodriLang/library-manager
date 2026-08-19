package com.rodrilang.librarymanager.purchasing.provider.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ProviderCatalogBookResponse(

        Long providerBookId,

        Long bookId,
        String isbn,
        String title,
        List<String> authors,
        String publisher,
        String coverUrl,

        String externalCode,

        BigDecimal providerPrice,

        Long inventoryId,
        Integer stock,
        Integer minimumStock,

        Long purchaseRequirementId,
        Integer requiredQuantity,

        Long preferredProviderId,
        String preferredProviderName,

        List<ProviderCatalogAlternativeResponse> alternativeProviders

) {

    public boolean inInventory() {
        return inventoryId != null;
    }

    public boolean pendingPurchase() {
        return purchaseRequirementId != null;
    }
}