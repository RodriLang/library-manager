package com.rodrilang.librarymanager.inventory.valuation.dto.response;

import java.math.BigDecimal;

public record ValuationCoverageResponse(

        Long coveredUnits,

        Long missingUnits,

        BigDecimal percentage

) {
}
