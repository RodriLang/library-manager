package com.rodrilang.librarymanager.profitability.service;

import com.rodrilang.librarymanager.profitability.dto.response.ProfitabilitySummaryResponse;
import com.rodrilang.librarymanager.profitability.model.CostCoverageStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ProfitabilityCalculator {

    private static final int MONEY_SCALE = 2;
    private static final int PERCENTAGE_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public ProfitabilitySummaryResponse calculate(
            BigDecimal netSalesAmount,
            int totalUnits,
            BigDecimal realCostAmount,
            BigDecimal estimatedCostAmount,
            int realCostUnits,
            int estimatedCostUnits
    ) {
        BigDecimal sales = money(netSalesAmount);
        BigDecimal realCost = money(realCostAmount);
        BigDecimal estimatedCost = money(estimatedCostAmount);
        BigDecimal knownCost = realCost.add(estimatedCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        int normalizedTotalUnits = Math.max(totalUnits, 0);
        int normalizedRealUnits = Math.max(realCostUnits, 0);
        int normalizedEstimatedUnits = Math.max(estimatedCostUnits, 0);
        int normalizedKnownUnits = Math.min(
                normalizedRealUnits + normalizedEstimatedUnits,
                normalizedTotalUnits
        );
        int unknownUnits = normalizedTotalUnits - normalizedKnownUnits;

        CostCoverageStatus status = coverageStatus(normalizedTotalUnits, normalizedKnownUnits);
        BigDecimal coverage = percentage(normalizedKnownUnits, normalizedTotalUnits);

        BigDecimal grossProfit = null;
        BigDecimal grossMargin = null;

        if (status == CostCoverageStatus.COMPLETE) {
            grossProfit = sales.subtract(knownCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (sales.signum() != 0) {
                grossMargin = grossProfit
                        .multiply(ONE_HUNDRED)
                        .divide(sales, PERCENTAGE_SCALE, RoundingMode.HALF_UP);
            }
        }

        return new ProfitabilitySummaryResponse(
                sales,
                knownCost,
                realCost,
                estimatedCost,
                normalizedTotalUnits,
                normalizedRealUnits,
                normalizedEstimatedUnits,
                normalizedKnownUnits,
                unknownUnits,
                coverage,
                status,
                grossProfit,
                grossMargin
        );
    }

    private CostCoverageStatus coverageStatus(int totalUnits, int knownUnits) {
        if (totalUnits == 0) {
            return CostCoverageStatus.NO_DATA;
        }
        if (knownUnits == 0) {
            return CostCoverageStatus.UNKNOWN;
        }
        if (knownUnits < totalUnits) {
            return CostCoverageStatus.PARTIAL;
        }

        return CostCoverageStatus.COMPLETE;
    }

    private BigDecimal percentage(int numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP);
        }

        return BigDecimal.valueOf(numerator)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(denominator), PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
