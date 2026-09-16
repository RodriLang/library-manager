package com.rodrilang.librarymanager.fiscal.service;

import com.rodrilang.librarymanager.fiscal.dto.request.IssueInvoiceRequest;
import com.rodrilang.librarymanager.fiscal.dto.response.FiscalDocumentResponse;

import java.util.Optional;

public interface FiscalDocumentService {

    FiscalDocumentResponse issueInvoice(Long saleId, IssueInvoiceRequest request);

    Optional<FiscalDocumentResponse> findBySaleId(Long saleId);

    FiscalDocumentResponse reconcile(Long documentId);
}
