package com.rodrilang.librarymanager.purchasing.model;

import com.rodrilang.librarymanager.purchasing.payment.model.PurchasePayment;
import com.rodrilang.librarymanager.purchasing.payment.model.PurchasePaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PurchasePaymentSummaryTest {

    @Test
    void pendingWhenThereAreNoActivePayments() {
        Purchase purchase = purchase("35760.00", List.of());

        assertEquals(new BigDecimal("0.00"), purchase.getPaidAmount());
        assertEquals(new BigDecimal("35760.00"), purchase.getPendingAmount());
        assertEquals(PurchasePaymentStatus.PENDING, purchase.getPaymentStatus());
    }

    @Test
    void partiallyPaidWhenActivePaymentsDoNotCoverTotal() {
        Purchase purchase = purchase(
                "35760.00",
                List.of(payment("20000.00", null))
        );

        assertEquals(new BigDecimal("20000.00"), purchase.getPaidAmount());
        assertEquals(new BigDecimal("15760.00"), purchase.getPendingAmount());
        assertEquals(PurchasePaymentStatus.PARTIALLY_PAID, purchase.getPaymentStatus());
    }

    @Test
    void paidWhenActivePaymentsCoverTotal() {
        Purchase purchase = purchase(
                "35760.00",
                List.of(
                        payment("20000.00", null),
                        payment("15760.00", null)
                )
        );

        assertEquals(new BigDecimal("35760.00"), purchase.getPaidAmount());
        assertEquals(new BigDecimal("0.00"), purchase.getPendingAmount());
        assertEquals(PurchasePaymentStatus.PAID, purchase.getPaymentStatus());
    }

    @Test
    void cancelledPaymentsDoNotCountTowardsPaidAmount() {
        Purchase purchase = purchase(
                "35760.00",
                List.of(
                        payment("20000.00", Instant.parse("2026-09-17T20:00:00Z")),
                        payment("10000.00", null)
                )
        );

        assertEquals(new BigDecimal("10000.00"), purchase.getPaidAmount());
        assertEquals(new BigDecimal("25760.00"), purchase.getPendingAmount());
        assertEquals(PurchasePaymentStatus.PARTIALLY_PAID, purchase.getPaymentStatus());
    }

    private Purchase purchase(String totalAmount, List<PurchasePayment> payments) {
        return Purchase.builder()
                .totalAmount(new BigDecimal(totalAmount))
                .payments(new ArrayList<>(payments))
                .build();
    }

    private PurchasePayment payment(String amount, Instant cancelledAt) {
        return PurchasePayment.builder()
                .amount(new BigDecimal(amount))
                .cancelledAt(cancelledAt)
                .build();
    }
}
