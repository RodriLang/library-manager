package com.rodrilang.librarymanager.fiscal.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ArcaInvoiceRequest(

        long representedCuit,
        int pointOfSale,
        int voucherType,
        long voucherNumber,
        LocalDate issueDate,
        int recipientDocumentType,
        long recipientDocumentNumber,
        int recipientVatConditionId,
        BigDecimal total,
        BigDecimal netAmount,
        BigDecimal exemptAmount

) {
}
