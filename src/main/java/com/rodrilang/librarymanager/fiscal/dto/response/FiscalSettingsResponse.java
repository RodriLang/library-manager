package com.rodrilang.librarymanager.fiscal.dto.response;

import com.rodrilang.librarymanager.fiscal.config.ArcaEnvironment;
import com.rodrilang.librarymanager.fiscal.model.ArcaAuthorizationStatus;
import com.rodrilang.librarymanager.fiscal.model.GrossIncomeRegime;
import com.rodrilang.librarymanager.fiscal.model.IssuerTaxCondition;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FiscalSettingsResponse(

        boolean configured,
        String cuit,
        String legalName,
        IssuerTaxCondition taxCondition,
        GrossIncomeRegime grossIncomeRegime,
        String grossIncomeNumber,
        LocalDate activityStartDate,
        String fiscalAddress,
        String city,
        String province,
        String postalCode,
        Integer pointOfSale,
        ArcaAuthorizationStatus arcaStatus,
        Instant verifiedAt,
        String lastVerificationError,
        ArcaEnvironment environment,
        boolean arcaClientEnabled,
        String delegateCuit,
        BigDecimal consumerFinalIdentificationThreshold

) {
}
