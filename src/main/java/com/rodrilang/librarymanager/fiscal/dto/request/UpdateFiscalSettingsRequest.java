package com.rodrilang.librarymanager.fiscal.dto.request;

import com.rodrilang.librarymanager.fiscal.model.GrossIncomeRegime;
import com.rodrilang.librarymanager.fiscal.model.IssuerTaxCondition;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateFiscalSettingsRequest(

        @NotBlank
        @Pattern(regexp = "\\d{11}", message = "El CUIT debe contener 11 dígitos.")
        String cuit,

        @NotBlank
        @Size(max = 200)
        String legalName,

        @NotNull
        IssuerTaxCondition taxCondition,

        @NotNull
        GrossIncomeRegime grossIncomeRegime,

        @Size(max = 50)
        String grossIncomeNumber,

        @NotNull
        LocalDate activityStartDate,

        @NotBlank
        @Size(max = 250)
        String fiscalAddress,

        @NotBlank
        @Size(max = 120)
        String city,

        @NotBlank
        @Size(max = 120)
        String province,

        @Size(max = 20)
        String postalCode,

        @NotNull
        @Min(1)
        @Max(99999)
        Integer pointOfSale

) {
}
