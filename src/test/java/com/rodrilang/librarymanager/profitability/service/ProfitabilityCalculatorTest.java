package com.rodrilang.librarymanager.profitability.service;

import com.rodrilang.librarymanager.profitability.dto.response.ProfitabilitySummaryResponse;
import com.rodrilang.librarymanager.profitability.model.CostCoverageStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProfitabilityCalculatorTest {

    private final ProfitabilityCalculator calculator = new ProfitabilityCalculator();

    @Test
    void calculatesGrossProfitWhenAllUnitsHaveKnownCost() {
        ProfitabilitySummaryResponse result = calculator.calculate(
                new BigDecimal("29800.00"),
                1,
                new BigDecimal("17880.00"),
                BigDecimal.ZERO,
                1,
                0
        );

        assertEquals(CostCoverageStatus.COMPLETE, result.costCoverageStatus());
        assertEquals(new BigDecimal("17880.00"), result.knownCostAmount());
        assertEquals(new BigDecimal("11920.00"), result.grossProfitAmount());
        assertEquals(new BigDecimal("40.00"), result.grossMarginPercentage());
        assertEquals(new BigDecimal("100.00"), result.costCoveragePercentage());
    }

    @Test
    void doesNotInventProfitWhenCostCoverageIsPartial() {
        ProfitabilitySummaryResponse result = calculator.calculate(
                new BigDecimal("50000.00"),
                2,
                new BigDecimal("12000.00"),
                BigDecimal.ZERO,
                1,
                0
        );

        assertEquals(CostCoverageStatus.PARTIAL, result.costCoverageStatus());
        assertEquals(new BigDecimal("50.00"), result.costCoveragePercentage());
        assertEquals(1, result.unknownCostUnits());
        assertNull(result.grossProfitAmount());
        assertNull(result.grossMarginPercentage());
    }

    @Test
    void separatesRealAndEstimatedCost() {
        ProfitabilitySummaryResponse result = calculator.calculate(
                new BigDecimal("30000.00"),
                2,
                new BigDecimal("8000.00"),
                new BigDecimal("7000.00"),
                1,
                1
        );

        assertEquals(new BigDecimal("15000.00"), result.knownCostAmount());
        assertEquals(new BigDecimal("8000.00"), result.realCostAmount());
        assertEquals(new BigDecimal("7000.00"), result.estimatedCostAmount());
        assertEquals(new BigDecimal("15000.00"), result.grossProfitAmount());
        assertEquals(new BigDecimal("50.00"), result.grossMarginPercentage());
    }

    @Test
    void returnsNoDataForEmptyPeriod() {
        ProfitabilitySummaryResponse result = calculator.calculate(
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0
        );

        assertEquals(CostCoverageStatus.NO_DATA, result.costCoverageStatus());
        assertEquals(new BigDecimal("0.00"), result.costCoveragePercentage());
        assertNull(result.grossProfitAmount());
    }
}
