package com.rodrilang.librarymanager.inventory.cost.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class InventoryCostCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public BigDecimal estimateFromDiscount(BigDecimal referencePrice, BigDecimal discountPercentage) {
        if (referencePrice == null) {
            throw new BusinessException("No hay un precio de referencia para calcular el costo estimado");
        }

        BigDecimal discount = percentage(discountPercentage);
        if (discount == null) {
            throw new BusinessException("Debe informarse el descuento para estimar el costo");
        }

        return referencePrice
                .multiply(ONE_HUNDRED.subtract(discount))
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal percentage(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0 || value.compareTo(ONE_HUNDRED) > 0) {
            throw new BusinessException("El descuento debe estar entre 0% y 100%");
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
