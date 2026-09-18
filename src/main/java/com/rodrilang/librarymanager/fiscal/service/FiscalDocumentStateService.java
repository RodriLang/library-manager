package com.rodrilang.librarymanager.fiscal.service;

import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.fiscal.client.dto.ArcaAuthorizationResult;
import com.rodrilang.librarymanager.fiscal.dto.internal.PrepareFiscalCreditNoteCommand;
import com.rodrilang.librarymanager.fiscal.dto.internal.PrepareFiscalInvoiceCommand;
import com.rodrilang.librarymanager.fiscal.dto.response.FiscalDocumentResponse;
import com.rodrilang.librarymanager.fiscal.model.BookstoreFiscalSettings;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocument;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentStatus;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentType;
import com.rodrilang.librarymanager.fiscal.model.FiscalVoucherClass;
import com.rodrilang.librarymanager.fiscal.repository.BookstoreFiscalSettingsRepository;
import com.rodrilang.librarymanager.fiscal.repository.FiscalDocumentRepository;
import com.rodrilang.librarymanager.sales.model.Sale;
import com.rodrilang.librarymanager.sales.model.SaleStatus;
import com.rodrilang.librarymanager.sales.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class FiscalDocumentStateService {

    private final FiscalDocumentRepository documentRepository;
    private final BookstoreFiscalSettingsRepository settingsRepository;
    private final SaleRepository saleRepository;
    private final UserRepository userRepository;
    private final FiscalDocumentMapper mapper;
    private final ArcaQrService qrService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FiscalDocumentResponse prepare(PrepareFiscalInvoiceCommand command) {
        Sale sale = saleRepository.findByIdAndBookstoreIdForUpdate(
                        command.saleId(),
                        command.bookstoreId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró la venta con ID: " + command.saleId()
                ));

        validateSaleCanBeInvoiced(sale);

        FiscalDocument document = documentRepository
                .findBySaleIdAndBookstoreIdAndDocumentTypeForUpdate(
                        sale.getId(),
                        command.bookstoreId(),
                        FiscalDocumentType.INVOICE
                )
                .orElseGet(() -> FiscalDocument.builder()
                        .bookstore(sale.getBookstore())
                        .sale(sale)
                        .createdBy(userRepository.getReferenceById(command.userId()))
                        .documentType(FiscalDocumentType.INVOICE)
                        .build());

        if (isFinalOrPending(document)) return mapper.toResponse(document);

        initializeInvoice(document, sale, command);
        documentRepository.saveAndFlush(document);
        return mapper.toResponse(document);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FiscalDocumentResponse prepareCreditNote(PrepareFiscalCreditNoteCommand command) {
        FiscalDocument invoice = documentRepository.findByIdAndBookstoreIdForUpdate(
                        command.invoiceId(),
                        command.bookstoreId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró la factura con ID: " + command.invoiceId()
                ));

        if (invoice.getDocumentType() != FiscalDocumentType.INVOICE) {
            throw new BusinessException("La nota de crédito debe asociarse a una factura.");
        }
        if (invoice.getStatus() != FiscalDocumentStatus.AUTHORIZED) {
            throw new BusinessException("Sólo se puede anular una factura autorizada por ARCA.");
        }
        if (invoice.getVoucherNumber() == null) {
            throw new BusinessException("La factura no tiene un número autorizado para asociar.");
        }

        Sale sale = saleRepository.findByIdAndBookstoreIdForUpdate(
                        invoice.getSale().getId(),
                        command.bookstoreId()
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró la venta asociada a la factura."
                ));

        FiscalDocument document = documentRepository
                .findBySaleIdAndBookstoreIdAndDocumentTypeForUpdate(
                        sale.getId(),
                        command.bookstoreId(),
                        FiscalDocumentType.CREDIT_NOTE
                )
                .orElseGet(() -> FiscalDocument.builder()
                        .bookstore(sale.getBookstore())
                        .sale(sale)
                        .createdBy(userRepository.getReferenceById(command.userId()))
                        .documentType(FiscalDocumentType.CREDIT_NOTE)
                        .associatedDocument(invoice)
                        .build());

        if (isFinalOrPending(document)) return mapper.toResponse(document);

        initializeCreditNote(document, invoice, sale, command);
        documentRepository.saveAndFlush(document);
        return mapper.toResponse(document);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FiscalDocumentResponse applyAuthorization(
            Long documentId,
            Long bookstoreId,
            ArcaAuthorizationResult result
    ) {
        FiscalDocument document = getForUpdate(documentId, bookstoreId);
        document.setArcaResult(result.result());
        document.setArcaObservations(limit(result.observations(), 2000));
        document.setArcaErrors(limit(result.errors(), 2000));

        if (!result.authorized()) {
            document.setStatus(FiscalDocumentStatus.REJECTED);
            documentRepository.flush();
            return mapper.toResponse(document);
        }

        BookstoreFiscalSettings settings = settingsRepository.findByBookstoreId(bookstoreId)
                .orElseThrow(() -> new BusinessException("No existe configuración fiscal."));

        document.setStatus(FiscalDocumentStatus.AUTHORIZED);
        document.setCae(result.cae());
        document.setCaeExpirationDate(result.caeExpirationDate());
        document.setAuthorizedAt(Instant.now());
        document.setQrUrl(qrService.buildUrl(document, settings));
        documentRepository.flush();
        return mapper.toResponse(document);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FiscalDocumentResponse markReconciliationRequired(
            Long documentId,
            Long bookstoreId,
            String message
    ) {
        FiscalDocument document = getForUpdate(documentId, bookstoreId);
        document.setStatus(FiscalDocumentStatus.RECONCILIATION_REQUIRED);
        document.setArcaErrors(limit(message, 2000));
        documentRepository.flush();
        return mapper.toResponse(document);
    }

    private void validateSaleCanBeInvoiced(Sale sale) {
        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw new BusinessException("Sólo se pueden facturar ventas completadas.");
        }
        if (sale.getTotal().signum() <= 0) {
            throw new BusinessException("No se puede emitir una factura por una venta con total cero.");
        }
    }

    private boolean isFinalOrPending(FiscalDocument document) {
        if (document.getId() == null) return false;
        return document.getStatus() == FiscalDocumentStatus.AUTHORIZED
                || document.getStatus() == FiscalDocumentStatus.AUTHORIZING
                || document.getStatus() == FiscalDocumentStatus.RECONCILIATION_REQUIRED;
    }

    private void initializeInvoice(
            FiscalDocument document,
            Sale sale,
            PrepareFiscalInvoiceCommand command
    ) {
        initializeAmounts(document, sale, command.voucherClass());
        document.setDocumentType(FiscalDocumentType.INVOICE);
        document.setAssociatedDocument(null);
        document.setReason(null);
        document.setVoucherTypeCode(command.voucherClass().getArcaCode());
        document.setPointOfSale(command.pointOfSale());
        document.setVoucherNumber(command.voucherNumber());
        document.setIssueDate(command.issueDate());
        document.setRecipientVatCondition(command.recipientVatCondition());
        document.setRecipientVatConditionId(command.recipientVatCondition().getArcaId());
        document.setRecipientDocumentType(command.recipientDocumentType());
        document.setRecipientDocumentTypeCode(command.recipientDocumentType().getArcaCode());
        document.setRecipientDocumentNumber(command.recipientDocumentNumber());
        document.setRecipientName(command.recipientName());
        document.setRecipientAddress(command.recipientAddress());
        resetAuthorization(document);
    }

    private void initializeCreditNote(
            FiscalDocument document,
            FiscalDocument invoice,
            Sale sale,
            PrepareFiscalCreditNoteCommand command
    ) {
        FiscalVoucherClass voucherClass = invoice.getVoucherClass();
        initializeAmounts(document, sale, voucherClass);
        document.setDocumentType(FiscalDocumentType.CREDIT_NOTE);
        document.setAssociatedDocument(invoice);
        document.setReason(command.reason());
        document.setVoucherTypeCode(voucherClass.getCreditNoteArcaCode());
        document.setPointOfSale(command.pointOfSale());
        document.setVoucherNumber(command.voucherNumber());
        document.setIssueDate(command.issueDate());
        document.setRecipientVatCondition(invoice.getRecipientVatCondition());
        document.setRecipientVatConditionId(invoice.getRecipientVatConditionId());
        document.setRecipientDocumentType(invoice.getRecipientDocumentType());
        document.setRecipientDocumentTypeCode(invoice.getRecipientDocumentTypeCode());
        document.setRecipientDocumentNumber(invoice.getRecipientDocumentNumber());
        document.setRecipientName(invoice.getRecipientName());
        document.setRecipientAddress(invoice.getRecipientAddress());
        resetAuthorization(document);
    }

    private void initializeAmounts(
            FiscalDocument document,
            Sale sale,
            FiscalVoucherClass voucherClass
    ) {
        document.setStatus(FiscalDocumentStatus.AUTHORIZING);
        document.setVoucherClass(voucherClass);
        document.setCurrency("PES");
        document.setExchangeRate(BigDecimal.ONE);

        if (voucherClass == FiscalVoucherClass.C) {
            document.setNetAmount(sale.getTotal());
            document.setExemptAmount(BigDecimal.ZERO.setScale(2));
        } else {
            document.setNetAmount(BigDecimal.ZERO.setScale(2));
            document.setExemptAmount(sale.getTotal());
        }

        document.setVatAmount(BigDecimal.ZERO.setScale(2));
        document.setTaxAmount(BigDecimal.ZERO.setScale(2));
        document.setTotalAmount(sale.getTotal());
    }

    private void resetAuthorization(FiscalDocument document) {
        document.setCae(null);
        document.setCaeExpirationDate(null);
        document.setAuthorizedAt(null);
        document.setArcaResult(null);
        document.setArcaObservations(null);
        document.setArcaErrors(null);
        document.setQrUrl(null);
    }

    private FiscalDocument getForUpdate(Long documentId, Long bookstoreId) {
        return documentRepository.findByIdAndBookstoreIdForUpdate(documentId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el comprobante fiscal con ID: " + documentId
                ));
    }

    private String limit(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
