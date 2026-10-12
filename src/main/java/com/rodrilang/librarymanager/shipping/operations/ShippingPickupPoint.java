package com.rodrilang.librarymanager.shipping.operations;
public record ShippingPickupPoint(String externalId, String name, String address, String postalCode, String city, String state, Double latitude, Double longitude) {}
