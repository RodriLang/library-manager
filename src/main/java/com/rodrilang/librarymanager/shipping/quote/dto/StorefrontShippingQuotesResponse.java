package com.rodrilang.librarymanager.shipping.quote.dto;

import java.util.List;

public record StorefrontShippingQuotesResponse(
        String postalCode,
        ShippingPackageEstimateResponse packageEstimate,
        List<StorefrontShippingQuoteResponse> quotes,
        List<String> warnings
) {}
