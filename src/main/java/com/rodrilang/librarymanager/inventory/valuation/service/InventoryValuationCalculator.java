package com.rodrilang.librarymanager.inventory.valuation.service;

import com.rodrilang.librarymanager.inventory.valuation.dto.response.InventoryValuationResponse;
import com.rodrilang.librarymanager.inventory.valuation.dto.response.ValuationCoverageResponse;
import com.rodrilang.librarymanager.inventory.valuation.repository.InventoryValuationSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Component
public class InventoryValuationCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int PERCENTAGE_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public InventoryValuationResponse calculate(
            LocalDate asOf,
            InventoryValuationSnapshot snapshot
    ) {
        long totalUnits = Math.max(snapshot.totalUnits(), 0);

        return new InventoryValuationResponse(
                asOf,
                totalUnits,
                money(snapshot.knownRetailValueAmount()),
                coverage(totalUnits, snapshot.currentPriceUnits()),
                money(snapshot.knownHistoricalCostAmount()),
                money(snapshot.realHistoricalCostAmount()),
                money(snapshot.estimatedHistoricalCostAmount()),
                coverage(totalUnits, snapshot.historicalCostUnits()),
                money(snapshot.knownReplacementCostAmount()),
                coverage(totalUnits, snapshot.replacementCostUnits())
        );
    }

    private ValuationCoverageResponse coverage(long totalUnits, long coveredUnits) {
        long normalizedCovered = Math.max(Math.min(coveredUnits, totalUnits), 0);
        long missingUnits = Math.max(totalUnits - normalizedCovered, 0);

        BigDecimal percentage = totalUnits == 0
                ? BigDecimal.ZERO.setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(normalizedCovered)
                        .multiply(ONE_HUNDRED)
                        .divide(BigDecimal.valueOf(totalUnits), PERCENTAGE_SCALE, RoundingMode.HALF_UP);

        return new ValuationCoverageResponse(
                normalizedCovered,
                missingUnits,
                percentage
        );
    }

    private BigDecimal money(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
