package com.rodrilang.librarymanager.shipping.quote.provider;

import com.rodrilang.librarymanager.shipping.model.ShippingDeliveryType;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;

import java.math.BigDecimal;

public record ShippingProviderQuote(
        ShippingProviderType provider,
        String serviceCode,
        String displayName,
        ShippingDeliveryType deliveryType,
        BigDecimal amount,
        Integer estimatedDaysMin,
        Integer estimatedDaysMax,
        String estimatedText,
        String externalQuoteId,
        String metadataJson
) {}
