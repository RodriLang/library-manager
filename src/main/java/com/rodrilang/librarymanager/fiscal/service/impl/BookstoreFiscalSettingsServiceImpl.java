package com.rodrilang.librarymanager.fiscal.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.fiscal.client.ArcaWsfeClient;
import com.rodrilang.librarymanager.fiscal.client.dto.ArcaPointOfSale;
import com.rodrilang.librarymanager.fiscal.config.ArcaProperties;
import com.rodrilang.librarymanager.fiscal.dto.request.UpdateFiscalSettingsRequest;
import com.rodrilang.librarymanager.fiscal.dto.response.FiscalSettingsResponse;
import com.rodrilang.librarymanager.fiscal.model.ArcaAuthorizationStatus;
import com.rodrilang.librarymanager.fiscal.model.BookstoreFiscalSettings;
import com.rodrilang.librarymanager.fiscal.model.GrossIncomeRegime;
import com.rodrilang.librarymanager.fiscal.repository.BookstoreFiscalSettingsRepository;
import com.rodrilang.librarymanager.fiscal.service.BookstoreFiscalSettingsService;
import com.rodrilang.librarymanager.fiscal.service.CuitValidator;
import com.rodrilang.librarymanager.service.BookstoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BookstoreFiscalSettingsServiceImpl implements BookstoreFiscalSettingsService {

    private final BookstoreFiscalSettingsRepository repository;
    private final BookstoreService bookstoreService;
    private final BookstoreContext bookstoreContext;
    private final CuitValidator cuitValidator;
    private final ArcaWsfeClient arcaClient;
    private final ArcaProperties arcaProperties;

    @Override
    @Transactional(readOnly = true)
    public FiscalSettingsResponse get() {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return repository.findByBookstoreId(bookstoreId)
                .map(this::toResponse)
                .orElseGet(this::emptyResponse);
    }

    @Override
    @Transactional
    public FiscalSettingsResponse update(UpdateFiscalSettingsRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        String cuit = normalizeCuit(request.cuit());

        if (repository.existsByCuitAndPointOfSaleAndBookstoreIdNot(
                cuit,
                request.pointOfSale(),
                bookstoreId
        )) {
            throw new BusinessException(
                    "Ese CUIT y punto de venta ya están configurados en otra librería de Anaquel."
            );
        }

        BookstoreFiscalSettings settings = repository.findByBookstoreIdForUpdate(bookstoreId)
                .orElseGet(() -> BookstoreFiscalSettings.builder()
                        .bookstore(bookstoreService.getEntityById(bookstoreId))
                        .build());

        boolean connectionRelevantChange = settings.getId() == null
                || !cuit.equals(settings.getCuit())
                || !request.pointOfSale().equals(settings.getPointOfSale());

        settings.setCuit(cuit);
        settings.setLegalName(request.legalName().trim());
        settings.setTaxCondition(request.taxCondition());
        settings.setGrossIncomeRegime(request.grossIncomeRegime());
        settings.setGrossIncomeNumber(normalizeGrossIncomeNumber(
                request.grossIncomeRegime(),
                request.grossIncomeNumber()
        ));
        settings.setActivityStartDate(request.activityStartDate());
        settings.setFiscalAddress(request.fiscalAddress().trim());
        settings.setCity(request.city().trim());
        settings.setProvince(request.province().trim());
        settings.setPostalCode(normalize(request.postalCode()));
        settings.setPointOfSale(request.pointOfSale());

        if (connectionRelevantChange) {
            settings.setArcaStatus(ArcaAuthorizationStatus.PENDING_AUTHORIZATION);
            settings.setVerifiedAt(null);
            settings.setLastVerificationError(null);
        }

        return toResponse(repository.save(settings));
    }

    @Override
    @Transactional
    public FiscalSettingsResponse verifyAuthorization() {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        BookstoreFiscalSettings settings = repository.findByBookstoreIdForUpdate(bookstoreId)
                .orElseThrow(() -> new BusinessException(
                        "Primero debés configurar los datos fiscales de la librería."
                ));

        try {
            long representedCuit = Long.parseLong(settings.getCuit());
            boolean pointOfSaleFound = arcaClient.getPointsOfSale(representedCuit).stream()
                    .anyMatch(point -> matchesConfiguredPoint(point, settings.getPointOfSale()));

            if (!pointOfSaleFound) {
                settings.setArcaStatus(ArcaAuthorizationStatus.ERROR);
                settings.setVerifiedAt(null);
                settings.setLastVerificationError(
                        "ARCA respondió correctamente, pero el punto de venta "
                                + settings.getPointOfSale()
                                + " no aparece activo para Facturación Electrónica por Web Services."
                );
                return toResponse(settings);
            }

            settings.setArcaStatus(ArcaAuthorizationStatus.VERIFIED);
            settings.setVerifiedAt(Instant.now());
            settings.setLastVerificationError(null);
            return toResponse(settings);
        } catch (RuntimeException exception) {
            settings.setArcaStatus(ArcaAuthorizationStatus.ERROR);
            settings.setVerifiedAt(null);
            settings.setLastVerificationError(limit(exception.getMessage(), 1000));
            return toResponse(settings);
        }
    }

    private boolean matchesConfiguredPoint(ArcaPointOfSale point, Integer configuredPoint) {
        return point.number() == configuredPoint && point.active();
    }

    private FiscalSettingsResponse toResponse(BookstoreFiscalSettings settings) {
        return new FiscalSettingsResponse(
                true,
                settings.getCuit(),
                settings.getLegalName(),
                settings.getTaxCondition(),
                settings.getGrossIncomeRegime(),
                settings.getGrossIncomeNumber(),
                settings.getActivityStartDate(),
                settings.getFiscalAddress(),
                settings.getCity(),
                settings.getProvince(),
                settings.getPostalCode(),
                settings.getPointOfSale(),
                settings.getArcaStatus(),
                settings.getVerifiedAt(),
                settings.getLastVerificationError(),
                arcaProperties.environment(),
                arcaProperties.enabled(),
                arcaProperties.delegateCuit(),
                arcaProperties.consumerFinalIdentificationThreshold()
        );
    }

    private FiscalSettingsResponse emptyResponse() {
        return new FiscalSettingsResponse(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ArcaAuthorizationStatus.PENDING_AUTHORIZATION,
                null,
                null,
                arcaProperties.environment(),
                arcaProperties.enabled(),
                arcaProperties.delegateCuit(),
                arcaProperties.consumerFinalIdentificationThreshold()
        );
    }

    private String normalizeGrossIncomeNumber(GrossIncomeRegime regime, String value) {
        String number = normalize(value);

        if ((regime == GrossIncomeRegime.LOCAL || regime == GrossIncomeRegime.MULTILATERAL_AGREEMENT)
                && number == null) {
            throw new BusinessException(
                    "Ingresá el número de inscripción en Ingresos Brutos para el régimen seleccionado."
            );
        }

        return regime == GrossIncomeRegime.EXEMPT || regime == GrossIncomeRegime.NOT_REGISTERED
                ? null
                : number;
    }

    private String normalizeCuit(String value) {
        String cuit = value == null ? "" : value.replaceAll("\\D", "");
        if (cuit.length() != 11) throw new BusinessException("El CUIT debe contener 11 dígitos.");
        if (!cuitValidator.isValid(cuit)) throw new BusinessException("El CUIT informado no es válido.");
        return cuit;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String limit(String value, int max) {
        if (value == null) return "No se pudo verificar la autorización con ARCA.";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
