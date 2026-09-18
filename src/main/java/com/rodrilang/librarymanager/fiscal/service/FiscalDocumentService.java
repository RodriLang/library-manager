package com.rodrilang.librarymanager.fiscal.service;

import com.rodrilang.librarymanager.fiscal.dto.request.IssueCreditNoteRequest;
import com.rodrilang.librarymanager.fiscal.dto.request.IssueInvoiceRequest;
import com.rodrilang.librarymanager.fiscal.dto.response.FiscalDocumentResponse;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentStatus;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FiscalDocumentService {

    FiscalDocumentResponse issueInvoice(Long saleId, IssueInvoiceRequest request);

    FiscalDocumentResponse issueCreditNote(Long invoiceId, IssueCreditNoteRequest request);

    Optional<FiscalDocumentResponse> findBySaleId(Long saleId);

    List<FiscalDocumentResponse> findAllBySaleId(Long saleId);

    FiscalDocumentResponse findById(Long documentId);

    Page<FiscalDocumentResponse> findAll(
            FiscalDocumentType documentType,
            FiscalDocumentStatus status,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    );

    FiscalDocumentResponse reconcile(Long documentId);
}
