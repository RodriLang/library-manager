package com.rodrilang.librarymanager.sales.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.payment.model.PaymentMethod;
import com.rodrilang.librarymanager.sales.dto.internal.SaleTotals;
import com.rodrilang.librarymanager.sales.dto.request.CreateSalePaymentRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SaleCalculatorTest {

    private final SaleCalculator calculator = new SaleCalculator();

    @Test
    void calculatesLineSubtotal() {
        BigDecimal subtotal = calculator.calculateLineSubtotal(
                new BigDecimal("12500.00"),
                2
        );

        assertEquals(new BigDecimal("25000.00"), subtotal);
    }

    @Test
    void calculatesTotalsWithDiscount() {
        SaleTotals totals = calculator.calculateTotals(
                List.of(
                        new BigDecimal("25000.00"),
                        new BigDecimal("10000.00")
                ),
                new BigDecimal("5000.00")
        );

        assertEquals(new BigDecimal("35000.00"), totals.subtotal());
        assertEquals(new BigDecimal("5000.00"), totals.discountAmount());
        assertEquals(new BigDecimal("30000.00"), totals.total());
    }

    @Test
    void rejectsDiscountGreaterThanSubtotal() {
        assertThrows(
                BusinessException.class,
                () -> calculator.calculateTotals(
                        List.of(new BigDecimal("10000.00")),
                        new BigDecimal("10000.01")
                )
        );
    }

    @Test
    void acceptsMultiplePaymentsWhenTheyMatchTotal() {
        calculator.validatePayments(
                new BigDecimal("30000.00"),
                List.of(
                        new CreateSalePaymentRequest(
                                PaymentMethod.CASH,
                                new BigDecimal("10000.00"),
                                null
                        ),
                        new CreateSalePaymentRequest(
                                PaymentMethod.DIGITAL_WALLET,
                                new BigDecimal("20000.00"),
                                "MP-123"
                        )
                )
        );
    }

    @Test
    void rejectsPaymentsWhenTheyDoNotMatchTotal() {
        assertThrows(
                BusinessException.class,
                () -> calculator.validatePayments(
                        new BigDecimal("30000.00"),
                        List.of(
                                new CreateSalePaymentRequest(
                                        PaymentMethod.CASH,
                                        new BigDecimal("29999.99"),
                                        null
                                )
                        )
                )
        );
    }

    @Test
    void allowsZeroTotalWithoutPayments() {
        calculator.validatePayments(
                new BigDecimal("0.00"),
                List.of()
        );
    }
}
