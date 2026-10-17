package com.rodrilang.librarymanager.shipping.shipment.dto;

public record UpdateShipmentMetadataRequest(
        String providerShipmentId,
        String trackingNumber,
        String trackingUrl,
        String labelUrl,
        String providerStatus,
        String providerStatusDetail
) {}
