package com.rodrilang.librarymanager.fiscal.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FiscalVoucherClassTest {

    @Test
    void exposesInvoiceAndCreditNoteArcaCodes() {
        assertEquals(1, FiscalVoucherClass.A.getArcaCode());
        assertEquals(3, FiscalVoucherClass.A.getCreditNoteArcaCode());

        assertEquals(6, FiscalVoucherClass.B.getArcaCode());
        assertEquals(8, FiscalVoucherClass.B.getCreditNoteArcaCode());

        assertEquals(11, FiscalVoucherClass.C.getArcaCode());
        assertEquals(13, FiscalVoucherClass.C.getCreditNoteArcaCode());
    }
}
