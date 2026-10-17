package com.rodrilang.librarymanager.shipping.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpdateShippingSettingsRequest(
        @Size(max = 150) String originName,
        @Size(max = 150) String originContactName,
        @Email @Size(max = 150) String originEmail,
        @Size(max = 50) String originPhone,
        @Size(max = 180) String originStreet,
        @Size(max = 40) String originStreetNumber,
        @Size(max = 80) String originFloorApartment,
        @Size(max = 20) String originPostalCode,
        @Size(max = 120) String originCity,
        @Size(max = 120) String originState,
        @Pattern(regexp = "^[A-Za-z]{2}$") String originCountryCode,
        @Min(0) @Max(30) Integer preparationDays,
        @DecimalMin("0.0") BigDecimal freeShippingThreshold,
        @DecimalMin("0.0") BigDecimal packagingSurcharge
) {}
