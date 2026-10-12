package com.rodrilang.librarymanager.shipping.operations;
public record ReturnShippingResult(String externalId, String trackingNumber, String trackingUrl, String labelUrl) {}
