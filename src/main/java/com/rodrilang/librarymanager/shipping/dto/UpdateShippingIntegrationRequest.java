package com.rodrilang.librarymanager.shipping.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateShippingIntegrationRequest(
        Boolean enabled,
        @Size(max = 30) Map<@Size(max = 80) String, @Size(max = 2000) String> credentials,
        @Size(max = 50) Map<@Size(max = 100) String, Object> settings
) {}
