package com.rodrilang.librarymanager.inventory.cost.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryCostCalculatorTest {

    private final InventoryCostCalculator calculator = new InventoryCostCalculator();

    @Test
    void estimatesCostFromReferencePriceAndDiscount() {
        BigDecimal result = calculator.estimateFromDiscount(
                new BigDecimal("20000.00"),
                new BigDecimal("40.00")
        );

        assertEquals(new BigDecimal("12000.00"), result);
    }

    @Test
    void roundsEstimatedCostToMoneyScale() {
        BigDecimal result = calculator.estimateFromDiscount(
                new BigDecimal("12345.67"),
                new BigDecimal("37.50")
        );

        assertEquals(new BigDecimal("7716.04"), result);
    }

    @Test
    void rejectsDiscountOutsideValidRange() {
        assertThrows(
                BusinessException.class,
                () -> calculator.estimateFromDiscount(
                        new BigDecimal("10000.00"),
                        new BigDecimal("100.01")
                )
        );
    }

    @Test
    void requiresReferencePriceForPercentageEstimation() {
        assertThrows(
                BusinessException.class,
                () -> calculator.estimateFromDiscount(null, new BigDecimal("40.00"))
        );
    }
}
