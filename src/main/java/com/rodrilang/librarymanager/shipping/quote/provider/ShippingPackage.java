package com.rodrilang.librarymanager.shipping.quote.provider;

import java.math.BigDecimal;

public record ShippingPackage(
        int weightGrams,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal depthCm,
        BigDecimal volumeCm3,
        BigDecimal declaredValue,
        boolean usedFallbackValues
) {}
