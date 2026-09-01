package com.rodrilang.librarymanager.editorialprice.dto.response;

import java.util.List;

public record EditorialPriceBookDetailResponse(
        EffectiveEditorialPriceDetailResponse current,
        List<EditorialPriceValidityResponse> validities,
        List<EffectiveEditorialPriceHistoryResponse> history
) {
}