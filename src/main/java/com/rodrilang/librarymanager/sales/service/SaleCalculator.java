package com.rodrilang.librarymanager.sales.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.sales.dto.internal.SaleTotals;
import com.rodrilang.librarymanager.sales.dto.request.CreateSalePaymentRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;

@Component
public class SaleCalculator {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    public BigDecimal calculateLineSubtotal(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null) {
            throw new BusinessException("El libro no tiene un precio de venta definido.");
        }
        if (unitPrice.signum() < 0) {
            throw new BusinessException("El precio de venta no puede ser negativo.");
        }
        if (quantity <= 0) {
            throw new BusinessException("La cantidad vendida debe ser mayor a cero.");
        }

        return money(unitPrice)
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    public SaleTotals calculateTotals(
            Collection<BigDecimal> lineSubtotals,
            BigDecimal requestedDiscount
    ) {
        BigDecimal subtotal = lineSubtotals.stream()
                .map(this::money)
                .reduce(zero(), BigDecimal::add)
                .setScale(MONEY_SCALE, MONEY_ROUNDING);

        BigDecimal discount = requestedDiscount != null
                ? money(requestedDiscount)
                : zero();

        if (discount.signum() < 0) {
            throw new BusinessException("El descuento no puede ser negativo.");
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new BusinessException("El descuento no puede superar el subtotal de la venta.");
        }

        return new SaleTotals(
                subtotal,
                discount,
                subtotal.subtract(discount).setScale(MONEY_SCALE, MONEY_ROUNDING)
        );
    }

    public void validatePayments(
            BigDecimal total,
            List<CreateSalePaymentRequest> payments
    ) {
        if (payments == null) {
            throw new BusinessException("Deben informarse los pagos de la venta.");
        }

        BigDecimal normalizedTotal = money(total);

        if (normalizedTotal.signum() == 0) {
            if (!payments.isEmpty()) {
                throw new BusinessException("Una venta con total cero no debe registrar pagos.");
            }
            return;
        }

        if (payments.isEmpty()) {
            throw new BusinessException("Debe informarse al menos un medio de pago.");
        }

        for (CreateSalePaymentRequest payment : payments) {
            if (payment == null || payment.method() == null) {
                throw new BusinessException("Debe especificarse el medio de pago.");
            }
            if (payment.amount() == null || payment.amount().signum() <= 0) {
                throw new BusinessException("El importe del pago debe ser mayor a cero.");
            }
        }

        BigDecimal paymentTotal = payments.stream()
                .map(CreateSalePaymentRequest::amount)
                .map(this::money)
                .reduce(zero(), BigDecimal::add)
                .setScale(MONEY_SCALE, MONEY_ROUNDING);

        if (paymentTotal.compareTo(normalizedTotal) != 0) {
            throw new BusinessException(
                    "La suma de los pagos debe coincidir con el total de la venta."
            );
        }
    }

    public BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw new BusinessException("El importe no puede ser nulo.");
        }

        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
