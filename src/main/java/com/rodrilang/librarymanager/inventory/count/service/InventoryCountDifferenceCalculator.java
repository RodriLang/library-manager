package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.inventory.count.model.InventoryCountDifferenceType;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountMode;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountResult;
import org.springframework.stereotype.Component;

@Component
public class InventoryCountDifferenceCalculator {

    public InventoryCountDifferenceType calculate(InventoryCountMode mode, InventoryCountResult result) {
        if (result.getCountedQuantity() == null) {
            return null;
        }

        if (mode == InventoryCountMode.ADDITIVE) {
            return result.isInventoryExistedBefore()
                    ? InventoryCountDifferenceType.ADDED
                    : InventoryCountDifferenceType.NEW;
        }

        int previous = result.getPreviousQuantity();
        int counted = result.getCountedQuantity();

        if (!result.isInventoryExistedBefore() && counted > 0) {
            return InventoryCountDifferenceType.NEW;
        }
        if (previous == counted) {
            return InventoryCountDifferenceType.MATCH;
        }
        if (counted == 0 && previous > 0) {
            return InventoryCountDifferenceType.MISSING;
        }

        return counted > previous
                ? InventoryCountDifferenceType.SURPLUS
                : InventoryCountDifferenceType.SHORTAGE;
    }
}
