package com.rodrilang.librarymanager.inventory.count.dto.response;

public record InventoryCountReportSummaryResponse(

        long comparedTitles,

        long matchedTitles,

        long surplusTitles,

        long shortageTitles,

        long missingTitles,

        long newTitles,

        long addedTitles,

        long previousUnits,

        long countedUnits,

        long netDifference,

        long pendingCatalogItems,

        long pendingPriceItems,

        long invalidItems,

        long supersededItems,

        long concurrentMovements

) {
}
