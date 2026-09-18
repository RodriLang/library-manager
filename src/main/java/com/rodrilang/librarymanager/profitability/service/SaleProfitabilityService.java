package com.rodrilang.librarymanager.profitability.service;

import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostType;
import com.rodrilang.librarymanager.inventory.cost.repository.InventoryCostAllocationRepository;
import com.rodrilang.librarymanager.inventory.cost.repository.projection.SaleCostAllocationProjection;
import com.rodrilang.librarymanager.profitability.dto.response.ProfitabilitySummaryResponse;
import com.rodrilang.librarymanager.sales.model.Sale;
import com.rodrilang.librarymanager.sales.model.SaleItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SaleProfitabilityService {

    private final InventoryCostAllocationRepository allocationRepository;
    private final ProfitabilityCalculator calculator;

    @Transactional(readOnly = true)
    public ProfitabilitySummaryResponse summarize(
            Sale sale,
            List<SaleItem> items
    ) {
        List<SaleCostAllocationProjection> allocations = allocationRepository
                .findCostSummaryByMovementReference(
                        InventoryMovementReferenceType.SALE,
                        sale.getId().toString()
                );

        BigDecimal realCost = BigDecimal.ZERO;
        BigDecimal estimatedCost = BigDecimal.ZERO;
        int realCostUnits = 0;
        int estimatedCostUnits = 0;

        for (SaleCostAllocationProjection allocation : allocations) {
            if (allocation.getUnitCost() == null || allocation.getQuantity() == null) {
                continue;
            }

            InventoryCostType costType = allocation.getCostType();
            if (costType != InventoryCostType.REAL && costType != InventoryCostType.ESTIMATED) {
                continue;
            }

            BigDecimal amount = allocation.getUnitCost()
                    .multiply(BigDecimal.valueOf(allocation.getQuantity()));

            if (costType == InventoryCostType.REAL) {
                realCost = realCost.add(amount);
                realCostUnits += allocation.getQuantity();
            } else {
                estimatedCost = estimatedCost.add(amount);
                estimatedCostUnits += allocation.getQuantity();
            }
        }

        int totalUnits = items.stream()
                .mapToInt(SaleItem::getQuantity)
                .sum();

        return calculator.calculate(
                sale.getTotal(),
                totalUnits,
                realCost,
                estimatedCost,
                realCostUnits,
                estimatedCostUnits
        );
    }
}
