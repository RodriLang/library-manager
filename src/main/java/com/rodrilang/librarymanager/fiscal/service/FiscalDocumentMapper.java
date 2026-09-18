package com.rodrilang.librarymanager.fiscal.service;

import com.rodrilang.librarymanager.fiscal.dto.response.FiscalDocumentResponse;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocument;
import org.springframework.stereotype.Component;

@Component
public class FiscalDocumentMapper {

    public FiscalDocumentResponse toResponse(FiscalDocument document) {
        FiscalDocument reversing = document.getReversingDocument();

        return new FiscalDocumentResponse(
                document.getId(),
                document.getSale().getId(),
                document.getDocumentType(),
                document.getStatus(),
                document.getVoucherClass(),
                document.getVoucherTypeCode(),
                document.getPointOfSale(),
                document.getVoucherNumber(),
                document.getIssueDate(),
                document.getNetAmount(),
                document.getExemptAmount(),
                document.getVatAmount(),
                document.getTaxAmount(),
                document.getTotalAmount(),
                document.getRecipientVatCondition(),
                document.getRecipientDocumentType(),
                document.getRecipientDocumentNumber(),
                document.getRecipientName(),
                document.getRecipientAddress(),
                document.getCae(),
                document.getCaeExpirationDate(),
                document.getAuthorizedAt(),
                document.getArcaObservations(),
                document.getArcaErrors(),
                document.getQrUrl(),
                document.getAssociatedDocument() != null
                        ? document.getAssociatedDocument().getId()
                        : null,
                reversing != null ? reversing.getId() : null,
                reversing != null ? reversing.getStatus() : null,
                document.getReason(),
                document.getCreatedBy().getId(),
                document.getCreatedBy().getDisplayName(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
