package com.rodrilang.librarymanager.fiscal.controller;

import com.rodrilang.librarymanager.fiscal.dto.request.IssueInvoiceRequest;
import com.rodrilang.librarymanager.fiscal.dto.response.FiscalDocumentResponse;
import com.rodrilang.librarymanager.fiscal.service.FiscalDocumentService;
import com.rodrilang.librarymanager.fiscal.service.FiscalPdfService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Facturación", description = "Emisión de comprobantes fiscales asociados a ventas")
public class FiscalDocumentController {

    private final FiscalDocumentService documentService;
    private final FiscalPdfService pdfService;

    @PostMapping("/sales/{saleId}/invoice")
    public ResponseEntity<FiscalDocumentResponse> issueInvoice(
            @PathVariable Long saleId,
            @Valid @RequestBody IssueInvoiceRequest request
    ) {
        return ResponseEntity.ok(documentService.issueInvoice(saleId, request));
    }

    @GetMapping("/sales/{saleId}/invoice")
    public ResponseEntity<FiscalDocumentResponse> findInvoice(
            @PathVariable Long saleId
    ) {
        return documentService.findBySaleId(saleId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/fiscal-documents/{documentId}/reconcile")
    public ResponseEntity<FiscalDocumentResponse> reconcile(
            @PathVariable Long documentId
    ) {
        return ResponseEntity.ok(documentService.reconcile(documentId));
    }

    @GetMapping(
            value = "/fiscal-documents/{documentId}/pdf",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> pdf(
            @PathVariable Long documentId
    ) {
        byte[] pdf = pdfService.generate(documentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.inline()
                        .filename("comprobante-" + documentId + ".pdf")
                        .build()
        );

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
