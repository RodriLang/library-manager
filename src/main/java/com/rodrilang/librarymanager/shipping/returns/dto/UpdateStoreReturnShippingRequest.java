package com.rodrilang.librarymanager.shipping.returns.dto;
public record UpdateStoreReturnShippingRequest(String providerReturnId, String trackingNumber, String trackingUrl) {}
