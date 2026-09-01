package com.rodrilang.librarymanager.editorialprice.dto.request;

public record EditorialPriceConflictResolutionRequest(

        Long selectedEditorialPriceId,

        ManualEditorialPriceRequest manualPrice,

        String note
) {
}