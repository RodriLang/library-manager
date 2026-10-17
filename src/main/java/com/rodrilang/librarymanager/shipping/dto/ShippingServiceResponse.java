package com.rodrilang.librarymanager.shipping.dto;

import com.rodrilang.librarymanager.shipping.model.ShippingDeliveryType;

import java.util.Map;

public record ShippingServiceResponse(
        String serviceCode,
        String displayName,
        ShippingDeliveryType deliveryType,
        boolean enabled,
        int sortOrder,
        Map<String, Object> settings
) {}
