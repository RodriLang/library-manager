package com.rodrilang.librarymanager.fiscal.client.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArcaInvoiceRequestTest {

    @Test
    void detectsAssociatedVoucherOnlyWhenAssociationIsComplete() {
        ArcaInvoiceRequest invoice = request(null, null, null);
        ArcaInvoiceRequest creditNote = request(11, 9999, 8L);

        assertFalse(invoice.hasAssociatedVoucher());
        assertTrue(creditNote.hasAssociatedVoucher());
    }

    private ArcaInvoiceRequest request(
            Integer associatedType,
            Integer associatedPointOfSale,
            Long associatedNumber
    ) {
        return new ArcaInvoiceRequest(
                20_123_456_789L,
                9999,
                13,
                1L,
                LocalDate.of(2026, 9, 17),
                99,
                0L,
                5,
                new BigDecimal("29800.00"),
                new BigDecimal("29800.00"),
                BigDecimal.ZERO,
                associatedType,
                associatedPointOfSale,
                associatedNumber,
                associatedNumber != null ? LocalDate.of(2026, 9, 17) : null
        );
    }
}
