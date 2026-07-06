package com.rodrilang.librarymanager.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EditorialPriceResponse(

        BigDecimal price,

        LocalDate validFrom

) {

    public static EditorialPriceResponse empty() {
        return new EditorialPriceResponse(
                null,
                null
        );
    }
}