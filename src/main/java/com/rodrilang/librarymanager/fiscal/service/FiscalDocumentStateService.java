package com.rodrilang.librarymanager.fiscal.service;

import com.rodrilang.librarymanager.auth.repositories.UserRepository;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.fiscal.client.dto.ArcaAuthorizationResult;
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

        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw new BusinessException("Sólo se pueden facturar ventas completadas.");
        }
        if (sale.getTotal().signum() <= 0) {
            throw new BusinessException("No se puede emitir una factura por una venta con total cero.");
        }

        FiscalDocument document = documentRepository
                .findBySaleIdAndDocumentTypeForUpdate(sale.getId(), FiscalDocumentType.INVOICE)
                .orElseGet(() -> FiscalDocument.builder()
                        .bookstore(sale.getBookstore())
                        .sale(sale)
                        .createdBy(userRepository.getReferenceById(command.userId()))
                        .documentType(FiscalDocumentType.INVOICE)
                        .build());

        if (document.getId() != null && document.getStatus() == FiscalDocumentStatus.AUTHORIZED) {
            return mapper.toResponse(document);
        }
        if (document.getId() != null && (
                document.getStatus() == FiscalDocumentStatus.AUTHORIZING
                        || document.getStatus() == FiscalDocumentStatus.RECONCILIATION_REQUIRED
        )) {
            return mapper.toResponse(document);
        }

        initialize(document, sale, command);
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

    private void initialize(
            FiscalDocument document,
            Sale sale,
            PrepareFiscalInvoiceCommand command
    ) {
        document.setStatus(FiscalDocumentStatus.AUTHORIZING);
        document.setVoucherClass(command.voucherClass());
        document.setVoucherTypeCode(command.voucherClass().getArcaCode());
        document.setPointOfSale(command.pointOfSale());
        document.setVoucherNumber(command.voucherNumber());
        document.setIssueDate(command.issueDate());
        document.setCurrency("PES");
        document.setExchangeRate(BigDecimal.ONE);

        if (command.voucherClass() == FiscalVoucherClass.C) {
            document.setNetAmount(sale.getTotal());
            document.setExemptAmount(BigDecimal.ZERO.setScale(2));
        } else {
            document.setNetAmount(BigDecimal.ZERO.setScale(2));
            document.setExemptAmount(sale.getTotal());
        }

        document.setVatAmount(BigDecimal.ZERO.setScale(2));
        document.setTaxAmount(BigDecimal.ZERO.setScale(2));
        document.setTotalAmount(sale.getTotal());
        document.setRecipientVatCondition(command.recipientVatCondition());
        document.setRecipientVatConditionId(command.recipientVatCondition().getArcaId());
        document.setRecipientDocumentType(command.recipientDocumentType());
        document.setRecipientDocumentTypeCode(command.recipientDocumentType().getArcaCode());
        document.setRecipientDocumentNumber(command.recipientDocumentNumber());
        document.setRecipientName(command.recipientName());
        document.setRecipientAddress(command.recipientAddress());
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
