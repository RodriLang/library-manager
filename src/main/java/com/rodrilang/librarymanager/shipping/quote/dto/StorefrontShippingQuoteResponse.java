package com.rodrilang.librarymanager.shipping.quote.dto;

import com.rodrilang.librarymanager.shipping.model.ShippingDeliveryType;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StorefrontShippingQuoteResponse(
        UUID quoteId,
        ShippingProviderType provider,
        String serviceCode,
        String displayName,
        ShippingDeliveryType deliveryType,
        BigDecimal amount,
        String currency,
        Integer estimatedDaysMin,
        Integer estimatedDaysMax,
        String estimatedText,
        boolean freeShipping,
        boolean recommended,
        int sortPriority,
        Instant expiresAt
) {}
