package com.rodrilang.librarymanager.shipping.dto;

import com.rodrilang.librarymanager.shipping.model.ShippingDeliveryType;
import jakarta.validation.constraints.*;

import java.util.Map;

public record UpdateShippingServiceRequest(
        @NotBlank @Size(max = 150) String displayName,
        @NotNull ShippingDeliveryType deliveryType,
        Boolean enabled,
        @Min(0) Integer sortOrder,
        @Size(max = 50) Map<@Size(max = 100) String, Object> settings
) {}
