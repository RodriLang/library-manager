package com.rodrilang.librarymanager.importer.price.validator;

import com.rodrilang.librarymanager.exception.BusinessException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

public final class PriceListImportDateValidator {

    private static final ZoneId ANAQUEL_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private PriceListImportDateValidator() {}

    public static LocalDate normalizeAndValidateValidFrom(LocalDate validFrom) {
        if (validFrom == null) throw new BusinessException("Debe indicar el período de vigencia de la lista.");

        YearMonth period = YearMonth.from(validFrom);
        YearMonth maxPeriod = YearMonth.now(ANAQUEL_ZONE).plusMonths(1);

        if (period.isAfter(maxPeriod)) {
            throw new BusinessException("El período de vigencia no puede ser mayor a un mes en el futuro.");
        }

        return period.atDay(1);
    }
}