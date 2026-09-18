package com.rodrilang.librarymanager.fiscal.dto.response;

import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentStatus;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentType;
import com.rodrilang.librarymanager.fiscal.model.FiscalVoucherClass;
import com.rodrilang.librarymanager.fiscal.model.RecipientDocumentType;
import com.rodrilang.librarymanager.fiscal.model.RecipientVatCondition;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FiscalDocumentResponse(

        Long id,
        Long saleId,
        FiscalDocumentType documentType,
        FiscalDocumentStatus status,
        FiscalVoucherClass voucherClass,
        Integer voucherTypeCode,
        Integer pointOfSale,
        Long voucherNumber,
        LocalDate issueDate,
        BigDecimal netAmount,
        BigDecimal exemptAmount,
        BigDecimal vatAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        RecipientVatCondition recipientVatCondition,
        RecipientDocumentType recipientDocumentType,
        String recipientDocumentNumber,
        String recipientName,
        String recipientAddress,
        String cae,
        LocalDate caeExpirationDate,
        Instant authorizedAt,
        String arcaObservations,
        String arcaErrors,
        String qrUrl,
        Long associatedDocumentId,
        Long reversingDocumentId,
        FiscalDocumentStatus reversingDocumentStatus,
        String reason,
        Long createdByUserId,
        String createdByName,
        Instant createdAt,
        Instant updatedAt

) {
}
