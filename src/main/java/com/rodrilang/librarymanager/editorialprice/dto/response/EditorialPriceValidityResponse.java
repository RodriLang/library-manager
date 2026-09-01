package com.rodrilang.librarymanager.editorialprice.dto.response;

import java.time.LocalDate;
import java.util.List;

public record EditorialPriceValidityResponse(
        LocalDate validFrom,
        boolean conflict,
        List<EditorialPriceSourceResponse> sources,
        EditorialPriceResolutionResponse resolution
) {
}