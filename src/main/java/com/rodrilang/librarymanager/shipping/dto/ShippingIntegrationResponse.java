package com.rodrilang.librarymanager.shipping.dto;

import com.rodrilang.librarymanager.shipping.model.ShippingCapability;
import com.rodrilang.librarymanager.shipping.model.ShippingIntegrationStatus;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ShippingIntegrationResponse(
        ShippingProviderType provider,
        String displayName,
        boolean requiresCredentials,
        Set<ShippingCapability> capabilities,
        boolean enabled,
        ShippingIntegrationStatus status,
        boolean credentialsConfigured,
        List<String> credentialKeys,
        Map<String, Object> settings,
        List<ShippingServiceResponse> services,
        String lastError,
        Instant configuredAt
) {}
