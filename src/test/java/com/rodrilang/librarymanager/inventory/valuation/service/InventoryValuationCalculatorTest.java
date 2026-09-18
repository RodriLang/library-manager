package com.rodrilang.librarymanager.inventory.valuation.service;

import com.rodrilang.librarymanager.inventory.valuation.dto.response.InventoryValuationResponse;
import com.rodrilang.librarymanager.inventory.valuation.repository.InventoryValuationSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryValuationCalculatorTest {

    private final InventoryValuationCalculator calculator = new InventoryValuationCalculator();

    @Test
    void keepsPartialAmountsSeparatedFromCoverage() {
        InventoryValuationSnapshot snapshot = new InventoryValuationSnapshot(
                10,
                8,
                new BigDecimal("200000.00"),
                7,
                new BigDecimal("90000.00"),
                new BigDecimal("60000.00"),
                new BigDecimal("30000.00"),
                6,
                new BigDecimal("120000.00")
        );

        InventoryValuationResponse result = calculator.calculate(
                LocalDate.of(2026, 9, 17),
                snapshot
        );

        assertEquals(new BigDecimal("80.00"), result.retailValueCoverage().percentage());
        assertEquals(new BigDecimal("70.00"), result.historicalCostCoverage().percentage());
        assertEquals(new BigDecimal("60.00"), result.replacementCostCoverage().percentage());
        assertEquals(new BigDecimal("90000.00"), result.knownHistoricalCostAmount());
        assertEquals(new BigDecimal("120000.00"), result.knownReplacementCostAmount());
    }

    @Test
    void returnsZeroCoverageForEmptyInventory() {
        InventoryValuationSnapshot snapshot = new InventoryValuationSnapshot(
                0,
                0,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO
        );

        InventoryValuationResponse result = calculator.calculate(
                LocalDate.of(2026, 9, 17),
                snapshot
        );

        assertEquals(new BigDecimal("0.00"), result.retailValueCoverage().percentage());
        assertEquals(new BigDecimal("0.00"), result.historicalCostCoverage().percentage());
        assertEquals(new BigDecimal("0.00"), result.replacementCostCoverage().percentage());
    }
}
