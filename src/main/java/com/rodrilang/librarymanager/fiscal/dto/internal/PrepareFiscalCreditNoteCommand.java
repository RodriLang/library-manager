package com.rodrilang.librarymanager.fiscal.dto.internal;

import java.time.LocalDate;

public record PrepareFiscalCreditNoteCommand(
        Long bookstoreId,
        Long invoiceId,
        Long userId,
        Integer pointOfSale,
        Long voucherNumber,
        LocalDate issueDate,
        String reason
) {
}
