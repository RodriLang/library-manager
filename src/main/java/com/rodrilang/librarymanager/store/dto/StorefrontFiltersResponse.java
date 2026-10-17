package com.rodrilang.librarymanager.store.dto;

import java.math.BigDecimal;
import java.util.List;

public record StorefrontFiltersResponse(
        List<StorefrontAuthorResponse> authors,
        List<StorefrontPublisherResponse> publishers,
        List<String> categories,
        List<String> genres,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {}
