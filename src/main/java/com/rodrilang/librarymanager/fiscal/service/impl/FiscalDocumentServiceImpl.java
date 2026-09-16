package com.rodrilang.librarymanager.fiscal.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.fiscal.client.ArcaWsfeClient;
import com.rodrilang.librarymanager.fiscal.client.dto.ArcaAuthorizationResult;
import com.rodrilang.librarymanager.fiscal.client.dto.ArcaInvoiceRequest;
import com.rodrilang.librarymanager.fiscal.client.dto.ArcaVoucherInfo;
import com.rodrilang.librarymanager.fiscal.config.ArcaProperties;
import com.rodrilang.librarymanager.fiscal.dto.internal.PrepareFiscalInvoiceCommand;
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
import com.rodrilang.librarymanager.fiscal.service.FiscalDocumentStateService;
import com.rodrilang.librarymanager.fiscal.service.FiscalDocumentService;
import com.rodrilang.librarymanager.fiscal.service.FiscalVoucherResolver;
import com.rodrilang.librarymanager.sales.model.Sale;
import com.rodrilang.librarymanager.sales.model.SaleStatus;
import com.rodrilang.librarymanager.sales.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FiscalDocumentServiceImpl implements FiscalDocumentService {

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

    @Override
    @Transactional
    public FiscalDocumentResponse issueInvoice(Long saleId, IssueInvoiceRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Long userId = bookstoreContext.getCurrentUserId();

        // El lock de configuración serializa la numeración fiscal de esta librería incluso entre instancias.
        BookstoreFiscalSettings settings = settingsRepository.findByBookstoreIdForUpdate(bookstoreId)
                .orElseThrow(() -> new BusinessException(
                        "La librería todavía no configuró sus datos fiscales."
                ));

        if (settings.getArcaStatus() != ArcaAuthorizationStatus.VERIFIED) {
            throw new BusinessException(
                    "La autorización con ARCA debe estar verificada antes de emitir comprobantes."
            );
        }

        FiscalDocument existing = documentRepository
                .findBySaleIdAndDocumentType(saleId, FiscalDocumentType.INVOICE)
                .orElse(null);

        if (existing != null) {
            if (existing.getStatus() == FiscalDocumentStatus.AUTHORIZED) {
                return mapper.toResponse(existing);
            }
            if (existing.getStatus() == FiscalDocumentStatus.AUTHORIZING
                    || existing.getStatus() == FiscalDocumentStatus.RECONCILIATION_REQUIRED) {
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

        // Si esta consulta falla todavía no se envió FECAESolicitar: el usuario puede reintentar sin riesgo.
        long voucherNumber = arcaClient.getLastAuthorized(
                representedCuit,
                settings.getPointOfSale(),
                voucherClass.getArcaCode()
        ) + 1;

        FiscalDocumentResponse prepared = stateService.prepare(
                new PrepareFiscalInvoiceCommand(
                        bookstoreId,
                        saleId,
                        userId,
                        voucherClass,
                        settings.getPointOfSale(),
                        voucherNumber,
                        LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")),
                        request.recipientVatCondition(),
                        request.documentType(),
                        normalizeDocumentNumber(request),
                        normalize(request.name()),
                        normalize(request.address())
                )
        );

        if (prepared.status() == FiscalDocumentStatus.AUTHORIZED) return prepared;
        if (prepared.status() == FiscalDocumentStatus.RECONCILIATION_REQUIRED
                || (prepared.status() == FiscalDocumentStatus.AUTHORIZING
                && !prepared.voucherNumber().equals(voucherNumber))) {
            throw new BusinessException(
                    "Existe una emisión anterior pendiente. Verificá el comprobante en ARCA antes de continuar."
            );
        }

        try {
            ArcaAuthorizationResult result = arcaClient.authorize(
                    toArcaRequest(prepared, representedCuit)
            );
            return stateService.applyAuthorization(prepared.id(), bookstoreId, result);
        } catch (ArcaCommunicationException exception) {
            // El checkpoint ya está confirmado en DB. Si ARCA procesó la solicitud, FECompConsultar la recuperará.
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
        return documentRepository.findBySaleIdAndBookstoreId(saleId, bookstoreId)
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

        return mapper.toResponse(document);
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
                && sale.getTotal().compareTo(
                        consumerFinalIdentificationThreshold()
                ) >= 0) {
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
            long representedCuit
    ) {
        long documentNumber = document.recipientDocumentNumber() == null
                ? 0L
                : Long.parseLong(document.recipientDocumentNumber());

        return new ArcaInvoiceRequest(
                representedCuit,
                document.pointOfSale(),
                document.voucherTypeCode(),
                document.voucherNumber(),
                document.issueDate(),
                document.recipientDocumentType().getArcaCode(),
                documentNumber,
                document.recipientVatCondition().getArcaId(),
                document.totalAmount(),
                document.netAmount(),
                document.exemptAmount()
        );
    }

    private String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
