package com.rodrilang.librarymanager.shipping.dto;

import java.util.List;

public record ShippingConfigurationResponse(
        ShippingSettingsResponse settings,
        List<ShippingIntegrationResponse> integrations
) {}
