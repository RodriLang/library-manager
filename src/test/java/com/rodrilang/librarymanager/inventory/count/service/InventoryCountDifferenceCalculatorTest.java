package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.inventory.count.model.InventoryCountDifferenceType;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountMode;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryCountDifferenceCalculatorTest {

    private final InventoryCountDifferenceCalculator calculator = new InventoryCountDifferenceCalculator();

    @Test
    void absoluteDetectsMatch() {
        assertThat(calculateAbsolute(5, 5, true)).isEqualTo(InventoryCountDifferenceType.MATCH);
    }

    @Test
    void absoluteDetectsSurplus() {
        assertThat(calculateAbsolute(5, 8, true)).isEqualTo(InventoryCountDifferenceType.SURPLUS);
    }

    @Test
    void absoluteDetectsShortage() {
        assertThat(calculateAbsolute(8, 5, true)).isEqualTo(InventoryCountDifferenceType.SHORTAGE);
    }

    @Test
    void absoluteDetectsMissingBook() {
        assertThat(calculateAbsolute(4, 0, true)).isEqualTo(InventoryCountDifferenceType.MISSING);
    }

    @Test
    void absoluteDetectsNewBook() {
        assertThat(calculateAbsolute(0, 3, false)).isEqualTo(InventoryCountDifferenceType.NEW);
    }

    @Test
    void additiveDistinguishesExistingAndNewInventory() {
        InventoryCountResult existing = result(7, 3, true);
        InventoryCountResult newInventory = result(0, 3, false);

        assertThat(calculator.calculate(InventoryCountMode.ADDITIVE, existing))
                .isEqualTo(InventoryCountDifferenceType.ADDED);
        assertThat(calculator.calculate(InventoryCountMode.ADDITIVE, newInventory))
                .isEqualTo(InventoryCountDifferenceType.NEW);
    }

    private InventoryCountDifferenceType calculateAbsolute(int previous, int counted, boolean existed) {
        return calculator.calculate(InventoryCountMode.ABSOLUTE, result(previous, counted, existed));
    }

    private InventoryCountResult result(int previous, int counted, boolean existed) {
        return InventoryCountResult.builder()
                .previousQuantity(previous)
                .countedQuantity(counted)
                .inventoryExistedBefore(existed)
                .build();
    }
}
