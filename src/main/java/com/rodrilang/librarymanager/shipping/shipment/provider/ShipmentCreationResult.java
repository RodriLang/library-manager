package com.rodrilang.librarymanager.shipping.shipment.provider;
public record ShipmentCreationResult(String providerShipmentId, String trackingNumber, String trackingUrl, String providerStatus, String providerStatusDetail) {}
