package com.rodrilang.librarymanager.importer.price.configuration.dto.response;

public record ProviderBookReconciliationResult(

        Long providerBookId,

        Long previousBookId,

        Long targetBookId,

        int movedPrices,

        int removedDuplicatePrices,

        boolean providerBookMerged,

        boolean previousBookDeleted

) {
}