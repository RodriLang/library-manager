package com.rodrilang.librarymanager.fiscal.dto.internal;

import com.rodrilang.librarymanager.fiscal.model.FiscalVoucherClass;
import com.rodrilang.librarymanager.fiscal.model.RecipientDocumentType;
import com.rodrilang.librarymanager.fiscal.model.RecipientVatCondition;

import java.time.LocalDate;

public record PrepareFiscalInvoiceCommand(

        Long bookstoreId,
        Long saleId,
        Long userId,
        FiscalVoucherClass voucherClass,
        Integer pointOfSale,
        Long voucherNumber,
        LocalDate issueDate,
        RecipientVatCondition recipientVatCondition,
        RecipientDocumentType recipientDocumentType,
        String recipientDocumentNumber,
        String recipientName,
        String recipientAddress

) {
}
