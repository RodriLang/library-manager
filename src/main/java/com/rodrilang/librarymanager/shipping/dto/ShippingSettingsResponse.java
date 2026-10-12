package com.rodrilang.librarymanager.shipping.dto;

import java.math.BigDecimal;

public record ShippingSettingsResponse(
        ShippingOriginResponse origin,
        int preparationDays,
        BigDecimal freeShippingThreshold,
        BigDecimal packagingSurcharge,
        boolean originComplete
) {}
