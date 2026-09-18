package com.rodrilang.librarymanager.fiscal.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.fiscal.client.ArcaWsfeClient;
import com.rodrilang.librarymanager.fiscal.client.dto.ArcaAuthorizationResult;
import com.rodrilang.librarymanager.fiscal.client.dto.ArcaInvoiceRequest;
import com.rodrilang.librarymanager.fiscal.client.dto.ArcaVoucherInfo;
import com.rodrilang.librarymanager.fiscal.config.ArcaProperties;
import com.rodrilang.librarymanager.fiscal.dto.internal.PrepareFiscalCreditNoteCommand;
import com.rodrilang.librarymanager.fiscal.dto.internal.PrepareFiscalInvoiceCommand;
import com.rodrilang.librarymanager.fiscal.dto.request.IssueCreditNoteRequest;
import com.rodrilang.librarymanager.fiscal.dto.request.IssueInvoiceRequest;
import com.rodrilang.librarymanager.fiscal.dto.response.FiscalDocumentResponse;
import com.rodrilang.librarymanager.fiscal.exception.ArcaCommunicationException;
import com.rodrilang.librarymanager.fiscal.model.ArcaAuthorizationStatus;
import com.rodrilang.librarymanager.fiscal.model.BookstoreFiscalSettings;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocument;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentStatus;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocumentType;
import com.rodrilang.librarymanager.fiscal.model.FiscalVoucherClass;
import com.rodrilang.librarymanager.fiscal.model.RecipientDocumentType;
import com.rodrilang.librarymanager.fiscal.model.RecipientVatCondition;
import com.rodrilang.librarymanager.fiscal.repository.BookstoreFiscalSettingsRepository;
import com.rodrilang.librarymanager.fiscal.repository.FiscalDocumentRepository;
import com.rodrilang.librarymanager.fiscal.service.ArcaQrService;
import com.rodrilang.librarymanager.fiscal.service.CuitValidator;
import com.rodrilang.librarymanager.fiscal.service.FiscalDocumentMapper;
import com.rodrilang.librarymanager.fiscal.service.FiscalDocumentService;
import com.rodrilang.librarymanager.fiscal.service.FiscalDocumentStateService;
import com.rodrilang.librarymanager.fiscal.service.FiscalVoucherResolver;
import com.rodrilang.librarymanager.sales.dto.request.CancelSaleRequest;
import com.rodrilang.librarymanager.sales.model.Sale;
import com.rodrilang.librarymanager.sales.model.SaleStatus;
import com.rodrilang.librarymanager.sales.repository.SaleRepository;
import com.rodrilang.librarymanager.sales.service.SaleCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FiscalDocumentServiceImpl implements FiscalDocumentService {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final FiscalDocumentRepository documentRepository;
    private final BookstoreFiscalSettingsRepository settingsRepository;
    private final SaleRepository saleRepository;
    private final BookstoreContext bookstoreContext;
    private final FiscalVoucherResolver voucherResolver;
    private final CuitValidator cuitValidator;
    private final FiscalDocumentMapper mapper;
    private final FiscalDocumentStateService stateService;
    private final ArcaWsfeClient arcaClient;
    private final ArcaProperties arcaProperties;
    private final ArcaQrService qrService;
    private final SaleCommandService saleCommandService;

    @Override
    @Transactional
    public FiscalDocumentResponse issueInvoice(Long saleId, IssueInvoiceRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Long userId = bookstoreContext.getCurrentUserId();
        BookstoreFiscalSettings settings = verifiedSettingsForUpdate(bookstoreId);

        FiscalDocument existing = documentRepository
                .findBySaleIdAndBookstoreIdAndDocumentType(
                        saleId,
                        bookstoreId,
                        FiscalDocumentType.INVOICE
                )
                .orElse(null);

        if (existing != null) {
            if (existing.getStatus() == FiscalDocumentStatus.AUTHORIZED) {
                return mapper.toResponse(existing);
            }
            if (isPending(existing)) {
                throw new BusinessException(
                        "La emisión anterior debe verificarse en ARCA antes de volver a emitir."
                );
            }
        }

        validateRecipient(request);

        Sale sale = saleRepository.findByIdAndBookstoreId(saleId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró la venta con ID: " + saleId
                ));

        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw new BusinessException("Sólo se pueden facturar ventas completadas.");
        }
        if (sale.getTotal().signum() <= 0) {
            throw new BusinessException("No se puede emitir una factura por una venta con total cero.");
        }

        validateRecipientForSale(request, sale);

        FiscalVoucherClass voucherClass = voucherResolver.resolve(
                settings.getTaxCondition(),
                request.recipientVatCondition()
        );
        long representedCuit = Long.parseLong(settings.getCuit());
        long voucherNumber = nextVoucherNumber(
                representedCuit,
                settings.getPointOfSale(),
                voucherClass.getArcaCode()
        );

        FiscalDocumentResponse prepared = stateService.prepare(
                new PrepareFiscalInvoiceCommand(
                        bookstoreId,
                        saleId,
                        userId,
                        voucherClass,
                        settings.getPointOfSale(),
                        voucherNumber,
                        LocalDate.now(ARGENTINA),
                        request.recipientVatCondition(),
                        request.documentType(),
                        normalizeDocumentNumber(request),
                        normalize(request.name()),
                        normalize(request.address())
                )
        );

        validatePreparedVoucher(prepared, voucherNumber);

        try {
            ArcaAuthorizationResult result = arcaClient.authorize(
                    toArcaRequest(prepared, representedCuit, null)
            );
            return stateService.applyAuthorization(prepared.id(), bookstoreId, result);
        } catch (ArcaCommunicationException exception) {
            return stateService.markReconciliationRequired(
                    prepared.id(),
                    bookstoreId,
                    exception.getMessage()
            );
        }
    }

    @Override
    @Transactional
    public FiscalDocumentResponse issueCreditNote(
            Long invoiceId,
            IssueCreditNoteRequest request
    ) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException("Debe indicarse el motivo de la nota de crédito.");
        }

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Long userId = bookstoreContext.getCurrentUserId();
        BookstoreFiscalSettings settings = verifiedSettingsForUpdate(bookstoreId);

        FiscalDocument invoice = documentRepository.findByIdAndBookstoreId(invoiceId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró la factura con ID: " + invoiceId
                ));

        validateInvoiceForCreditNote(invoice, bookstoreId);

        FiscalDocument existing = documentRepository
                .findBySaleIdAndBookstoreIdAndDocumentType(
                        invoice.getSale().getId(),
                        bookstoreId,
                        FiscalDocumentType.CREDIT_NOTE
                )
                .orElse(null);

        if (existing != null) {
            if (existing.getStatus() == FiscalDocumentStatus.AUTHORIZED) {
                ensureSaleCancelled(existing);
                return mapper.toResponse(existing);
            }
            if (isPending(existing)) {
                throw new BusinessException(
                        "La nota de crédito anterior debe verificarse en ARCA antes de volver a emitir."
                );
            }
        }

        long representedCuit = Long.parseLong(settings.getCuit());
        long voucherNumber = nextVoucherNumber(
                representedCuit,
                settings.getPointOfSale(),
                invoice.getVoucherClass().getCreditNoteArcaCode()
        );

        FiscalDocumentResponse prepared = stateService.prepareCreditNote(
                new PrepareFiscalCreditNoteCommand(
                        bookstoreId,
                        invoiceId,
                        userId,
                        settings.getPointOfSale(),
                        voucherNumber,
                        LocalDate.now(ARGENTINA),
                        normalize(request.reason())
                )
        );

        if (prepared.status() == FiscalDocumentStatus.AUTHORIZED) {
            FiscalDocument authorized = documentRepository.findByIdAndBookstoreId(
                            prepared.id(),
                            bookstoreId
                    )
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No se encontró la nota de crédito autorizada."
                    ));
            ensureSaleCancelled(authorized);
            return mapper.toResponse(authorized);
        }

        validatePreparedVoucher(prepared, voucherNumber);

        try {
            ArcaAuthorizationResult result = arcaClient.authorize(
                    toArcaRequest(prepared, representedCuit, invoice)
            );
            FiscalDocumentResponse authorized = stateService.applyAuthorization(
                    prepared.id(),
                    bookstoreId,
                    result
            );

            if (authorized.status() == FiscalDocumentStatus.AUTHORIZED) {
                FiscalDocument creditNote = documentRepository.findByIdAndBookstoreId(
                                authorized.id(),
                                bookstoreId
                        )
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "No se encontró la nota de crédito autorizada."
                        ));
                ensureSaleCancelled(creditNote);
                return mapper.toResponse(creditNote);
            }

            return authorized;
        } catch (ArcaCommunicationException exception) {
            return stateService.markReconciliationRequired(
                    prepared.id(),
                    bookstoreId,
                    exception.getMessage()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FiscalDocumentResponse> findBySaleId(Long saleId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return documentRepository.findBySaleIdAndBookstoreIdAndDocumentType(
                        saleId,
                        bookstoreId,
                        FiscalDocumentType.INVOICE
                )
                .map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FiscalDocumentResponse> findAllBySaleId(Long saleId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return documentRepository.findAllBySaleIdAndBookstoreIdOrderByCreatedAtAsc(
                        saleId,
                        bookstoreId
                )
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FiscalDocumentResponse findById(Long documentId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return documentRepository.findByIdAndBookstoreId(documentId, bookstoreId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el comprobante fiscal con ID: " + documentId
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FiscalDocumentResponse> findAll(
            FiscalDocumentType documentType,
            FiscalDocumentStatus status,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException("La fecha desde no puede ser posterior a la fecha hasta.");
        }

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return documentRepository.search(
                        bookstoreId,
                        documentType,
                        status,
                        from,
                        to,
                        pageable
                )
                .map(mapper::toResponse);
    }

    @Override
    @Transactional
    public FiscalDocumentResponse reconcile(Long documentId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        BookstoreFiscalSettings settings = settingsRepository.findByBookstoreIdForUpdate(bookstoreId)
                .orElseThrow(() -> new BusinessException("No existe configuración fiscal."));

        FiscalDocument document = documentRepository.findByIdAndBookstoreIdForUpdate(documentId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el comprobante fiscal con ID: " + documentId
                ));

        if (document.getStatus() == FiscalDocumentStatus.AUTHORIZED) {
            if (document.getDocumentType() == FiscalDocumentType.CREDIT_NOTE) {
                ensureSaleCancelled(document);
            }
            return mapper.toResponse(document);
        }
        if (document.getVoucherNumber() == null) {
            throw new BusinessException("El comprobante no tiene un número reservado para conciliar.");
        }

        long representedCuit = Long.parseLong(settings.getCuit());
        Optional<ArcaVoucherInfo> remote = arcaClient.consult(
                representedCuit,
                document.getPointOfSale(),
                document.getVoucherTypeCode(),
                document.getVoucherNumber()
        );

        if (remote.isEmpty()) {
            document.setStatus(FiscalDocumentStatus.REJECTED);
            document.setArcaErrors(
                    "ARCA no encontró un comprobante autorizado con el número reservado. Podés volver a intentar la emisión."
            );
            documentRepository.flush();
            return mapper.toResponse(document);
        }

        ArcaVoucherInfo voucher = remote.get();
        if (voucher.total().compareTo(document.getTotalAmount()) != 0) {
            throw new BusinessException(
                    "ARCA devolvió un comprobante con el mismo número pero un importe distinto. Requiere revisión manual."
            );
        }

        document.setStatus(FiscalDocumentStatus.AUTHORIZED);
        document.setCae(voucher.cae());
        document.setCaeExpirationDate(voucher.caeExpirationDate());
        document.setAuthorizedAt(Instant.now());
        document.setArcaResult(voucher.result());
        document.setArcaObservations(voucher.observations());
        document.setArcaErrors(null);
        document.setQrUrl(qrService.buildUrl(document, settings));
        documentRepository.flush();

        if (document.getDocumentType() == FiscalDocumentType.CREDIT_NOTE) {
            ensureSaleCancelled(document);
        }

        return mapper.toResponse(document);
    }

    private BookstoreFiscalSettings verifiedSettingsForUpdate(Long bookstoreId) {
        BookstoreFiscalSettings settings = settingsRepository.findByBookstoreIdForUpdate(bookstoreId)
                .orElseThrow(() -> new BusinessException(
                        "La librería todavía no configuró sus datos fiscales."
                ));

        if (settings.getArcaStatus() != ArcaAuthorizationStatus.VERIFIED) {
            throw new BusinessException(
                    "La autorización con ARCA debe estar verificada antes de emitir comprobantes."
            );
        }

        return settings;
    }

    private void validateInvoiceForCreditNote(FiscalDocument invoice, Long bookstoreId) {
        if (invoice.getDocumentType() != FiscalDocumentType.INVOICE) {
            throw new BusinessException("Sólo puede emitirse una nota de crédito desde una factura.");
        }
        if (invoice.getStatus() != FiscalDocumentStatus.AUTHORIZED) {
            throw new BusinessException("Sólo puede anularse una factura autorizada por ARCA.");
        }
        if (invoice.getVoucherNumber() == null) {
            throw new BusinessException("La factura no tiene un número autorizado para asociar.");
        }

        if (invoice.getSale().getStatus() == SaleStatus.CANCELLED) {
            FiscalDocument creditNote = documentRepository.findBySaleIdAndBookstoreIdAndDocumentType(
                            invoice.getSale().getId(),
                            bookstoreId,
                            FiscalDocumentType.CREDIT_NOTE
                    )
                    .orElse(null);

            if (creditNote == null || creditNote.getStatus() != FiscalDocumentStatus.AUTHORIZED) {
                throw new BusinessException(
                        "La venta ya está cancelada pero no posee una nota de crédito autorizada. Requiere revisión manual."
                );
            }
        }
    }

    private long nextVoucherNumber(long cuit, int pointOfSale, int voucherType) {
        return arcaClient.getLastAuthorized(cuit, pointOfSale, voucherType) + 1;
    }

    private void validatePreparedVoucher(FiscalDocumentResponse prepared, long expectedVoucherNumber) {
        if (prepared.status() == FiscalDocumentStatus.AUTHORIZED) return;

        if (prepared.status() == FiscalDocumentStatus.RECONCILIATION_REQUIRED
                || (prepared.status() == FiscalDocumentStatus.AUTHORIZING
                && !prepared.voucherNumber().equals(expectedVoucherNumber))) {
            throw new BusinessException(
                    "Existe una emisión anterior pendiente. Verificá el comprobante en ARCA antes de continuar."
            );
        }
    }

    private boolean isPending(FiscalDocument document) {
        return document.getStatus() == FiscalDocumentStatus.AUTHORIZING
                || document.getStatus() == FiscalDocumentStatus.RECONCILIATION_REQUIRED;
    }

    private void ensureSaleCancelled(FiscalDocument creditNote) {
        if (creditNote.getDocumentType() != FiscalDocumentType.CREDIT_NOTE
                || creditNote.getStatus() != FiscalDocumentStatus.AUTHORIZED) {
            return;
        }

        if (creditNote.getSale().getStatus() == SaleStatus.CANCELLED) return;

        saleCommandService.cancel(
                creditNote.getSale().getId(),
                new CancelSaleRequest(creditNote.getReason())
        );
    }

    private void validateRecipient(IssueInvoiceRequest request) {
        if (request == null) throw new BusinessException("Deben informarse los datos del receptor.");
        if (request.recipientVatCondition() == null || request.documentType() == null) {
            throw new BusinessException("Debe indicarse la condición de IVA y el tipo de documento del receptor.");
        }

        if (request.documentType() == RecipientDocumentType.CONSUMIDOR_FINAL) {
            if (request.recipientVatCondition() != RecipientVatCondition.CONSUMIDOR_FINAL) {
                throw new BusinessException(
                        "El receptor sin identificar sólo puede usarse con condición Consumidor Final."
                );
            }
            return;
        }

        if (request.recipientVatCondition() != RecipientVatCondition.CONSUMIDOR_FINAL
                && request.documentType() != RecipientDocumentType.CUIT) {
            throw new BusinessException(
                    "Para receptores con condición fiscal informada debe utilizarse CUIT."
            );
        }

        String documentNumber = digits(request.documentNumber());
        if (request.documentType() == RecipientDocumentType.CUIT) {
            if (documentNumber.length() != 11) {
                throw new BusinessException("El CUIT del receptor debe contener 11 dígitos.");
            }
            if (!cuitValidator.isValid(documentNumber)) {
                throw new BusinessException("El CUIT del receptor no es válido.");
            }
        }
        if (request.documentType() == RecipientDocumentType.DNI && documentNumber.length() < 7) {
            throw new BusinessException("El DNI del receptor no es válido.");
        }
    }

    private void validateRecipientForSale(IssueInvoiceRequest request, Sale sale) {
        boolean consumerFinal = request.recipientVatCondition()
                == RecipientVatCondition.CONSUMIDOR_FINAL;

        if (!consumerFinal) {
            if (request.name() == null || request.name().isBlank()) {
                throw new BusinessException("Debe informarse el nombre o razón social del receptor.");
            }
            if (request.address() == null || request.address().isBlank()) {
                throw new BusinessException("Debe informarse el domicilio del receptor.");
            }
        }

        if (consumerFinal
                && request.documentType() == RecipientDocumentType.CONSUMIDOR_FINAL
                && sale.getTotal().compareTo(consumerFinalIdentificationThreshold()) >= 0) {
            throw new BusinessException(
                    "Por el importe de la operación debe identificarse al consumidor final con DNI o CUIT."
            );
        }
    }

    private BigDecimal consumerFinalIdentificationThreshold() {
        BigDecimal configured = arcaProperties.consumerFinalIdentificationThreshold();
        return configured == null ? new BigDecimal("10000000") : configured;
    }

    private String normalizeDocumentNumber(IssueInvoiceRequest request) {
        if (request.documentType() == RecipientDocumentType.CONSUMIDOR_FINAL) return null;
        return digits(request.documentNumber());
    }

    private ArcaInvoiceRequest toArcaRequest(
            FiscalDocumentResponse document,
            long representedCuit,
            FiscalDocument associatedDocument
    ) {
        long recipientDocumentNumber = document.recipientDocumentNumber() == null
                ? 0L
                : Long.parseLong(document.recipientDocumentNumber());

        return new ArcaInvoiceRequest(
                representedCuit,
                document.pointOfSale(),
                document.voucherTypeCode(),
                document.voucherNumber(),
                document.issueDate(),
                document.recipientDocumentType().getArcaCode(),
                recipientDocumentNumber,
                document.recipientVatCondition().getArcaId(),
                document.totalAmount(),
                document.netAmount(),
                document.exemptAmount(),
                associatedDocument != null ? associatedDocument.getVoucherTypeCode() : null,
                associatedDocument != null ? associatedDocument.getPointOfSale() : null,
                associatedDocument != null ? associatedDocument.getVoucherNumber() : null,
                associatedDocument != null ? associatedDocument.getIssueDate() : null
        );
    }

    private String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
