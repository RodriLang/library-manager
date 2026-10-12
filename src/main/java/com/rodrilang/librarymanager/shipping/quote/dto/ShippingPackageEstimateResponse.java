package com.rodrilang.librarymanager.shipping.quote.dto;

import java.math.BigDecimal;

public record ShippingPackageEstimateResponse(
        int weightGrams,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal depthCm,
        BigDecimal volumeCm3,
        boolean usedFallbackValues
) {}
