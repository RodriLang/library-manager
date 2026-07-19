package com.rodrilang.librarymanager.importer.price.validator;

import com.rodrilang.librarymanager.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class PriceListImportDateValidator {

    private PriceListImportDateValidator() {
    }

    public static void validateValidFrom(LocalDate validFrom) {

        if (validFrom == null) {
            throw new BusinessException("Debe indicar la fecha de vigencia de la lista.");
        }

        if (validFrom.isAfter(LocalDate.now().plusMonths(1))) {
            throw new BusinessException(
                    "La fecha de vigencia no puede ser mayor a un mes en el futuro."
            );
        }
    }
}