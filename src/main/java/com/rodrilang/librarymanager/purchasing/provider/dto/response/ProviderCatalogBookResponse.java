package com.rodrilang.librarymanager.purchasing.provider.dto.response;

import com.rodrilang.librarymanager.provider.catalog.enums.ProviderBookSource;
import com.rodrilang.librarymanager.provider.catalog.enums.ProviderBookVerificationStatus;

import java.math.BigDecimal;
import java.time.Instant;
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

        Instant providerFirstSeenAt,
        Instant providerLastSeenAt,
        ProviderBookSource providerCatalogSource,
        ProviderBookVerificationStatus providerCatalogStatus,

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