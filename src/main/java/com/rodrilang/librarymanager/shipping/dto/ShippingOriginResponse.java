package com.rodrilang.librarymanager.shipping.dto;

public record ShippingOriginResponse(
        String name,
        String contactName,
        String email,
        String phone,
        String street,
        String streetNumber,
        String floorApartment,
        String postalCode,
        String city,
        String state,
        String countryCode
) {}
