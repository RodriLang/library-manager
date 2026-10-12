package com.rodrilang.librarymanager.shipping.quote.dto;

import jakarta.validation.constraints.Size;

public record ShippingAddressRequest(
        @Size(max = 20) String postalCode,
        @Size(max = 120) String city,
        @Size(max = 120) String state,
        @Size(max = 180) String street,
        @Size(max = 40) String streetNumber,
        @Size(max = 80) String floorApartment
) {}
