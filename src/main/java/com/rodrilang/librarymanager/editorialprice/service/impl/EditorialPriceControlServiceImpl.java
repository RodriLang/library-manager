package com.rodrilang.librarymanager.editorialprice.service.impl;

import com.rodrilang.librarymanager.editorialprice.dto.internal.EffectiveEditorialPriceRefreshResult;
import com.rodrilang.librarymanager.editorialprice.dto.request.EditorialPriceConfirmationRequest;
import com.rodrilang.librarymanager.editorialprice.dto.request.EditorialPriceConflictResolutionRequest;
import com.rodrilang.librarymanager.editorialprice.dto.request.EditorialPriceMetadataUpdateRequest;
import com.rodrilang.librarymanager.editorialprice.dto.request.ManualEditorialPriceRequest;
import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceConfirmationSourceType;
import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceOrigin;
import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceResolutionType;
import com.rodrilang.librarymanager.editorialprice.model.EditorialPriceConfirmation;
import com.rodrilang.librarymanager.editorialprice.model.EditorialPriceResolution;
import com.rodrilang.librarymanager.editorialprice.repository.EditorialPriceConfirmationRepository;
import com.rodrilang.librarymanager.editorialprice.repository.EditorialPriceResolutionRepository;
import com.rodrilang.librarymanager.editorialprice.service.EditorialPriceControlService;
import com.rodrilang.librarymanager.editorialprice.service.EditorialPriceHealthCacheService;
import com.rodrilang.librarymanager.editorialprice.service.EffectiveEditorialPriceService;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.configuration.repository.PriceListProviderRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubePriceSyncRequestedEvent;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.EditorialPriceRepository;
import com.rodrilang.librarymanager.repository.InventoryEditorialPriceSyncRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EditorialPriceControlServiceImpl implements EditorialPriceControlService {

    private static final ZoneId ANAQUEL_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final BookRepository bookRepository;

    private final EditorialPriceRepository editorialPriceRepository;
    private final EditorialPriceConfirmationRepository confirmationRepository;
    private final EditorialPriceResolutionRepository resolutionRepository;

    private final PriceListProviderRepository providerRepository;

    private final EffectiveEditorialPriceService effectivePriceService;

    private final InventoryEditorialPriceSyncRepository inventoryEditorialPriceSyncRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final EditorialPriceHealthCacheService editorialPriceHealthCacheService;

    @Override
    @Transactional
    public void createManualPrice(Long bookId, ManualEditorialPriceRequest request, String username) {
        EditorialPrice price = createManualPriceInternal(bookId, request, username);

        EffectiveEditorialPriceRefreshResult refresh =
                effectivePriceService.refreshForBooks(
                        Set.of(bookId),
                        price.getValidFrom()
                );

        syncDownstream(refresh.changedBookIds());

        editorialPriceHealthCacheService.evictSummaryAfterCommit();
    }

    @Override
    @Transactional
    public void confirmPrice(Long editorialPriceId, EditorialPriceConfirmationRequest request, String username) {
        if (request == null) {
            throw new BusinessException("Debe especificarse la confirmación.");
        }

        EditorialPrice price = editorialPriceRepository.findById(editorialPriceId)
                .orElseThrow(() -> new BusinessException("No se encontró el precio editorial."));

        if (!price.isActive()) {
            throw new BusinessException("No se puede confirmar un precio inactivo.");
        }

        validateConfirmation(request);

        PriceListProvider provider = resolveConfirmationProvider(request);

        EditorialPriceConfirmation confirmation =
                EditorialPriceConfirmation.builder()
                        .editorialPrice(price)
                        .confirmedOn(request.confirmedOn())
                        .sourceType(request.sourceType())
                        .provider(provider)
                        .externalSourceType(request.externalSourceType())
                        .sourceName(normalizeNullable(request.sourceName()))
                        .sourceUrl(normalizeNullable(request.sourceUrl()))
                        .note(normalizeNullable(request.note()))
                        .createdByUsername(username)
                        .build();

        confirmationRepository.save(confirmation);

        editorialPriceHealthCacheService.evictSummaryAfterCommit();
    }

    @Override
    @Transactional
    public void resolveConflict(
            Long bookId,
            LocalDate validFrom,
            EditorialPriceConflictResolutionRequest request,
            String username
    ) {
        if (request == null) throw new BusinessException("Debe especificarse la resolución.");
        if (validFrom == null) throw new BusinessException("La fecha de vigencia es obligatoria.");

        boolean hasSelectedPrice = request.selectedEditorialPriceId() != null;
        boolean hasManualPrice = request.manualPrice() != null;

        if (hasSelectedPrice == hasManualPrice) {
            throw new BusinessException("Debe seleccionar una fuente existente o cargar un precio manual, pero no ambos.");
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BusinessException("No se encontró el libro."));

        List<EditorialPrice> sources = editorialPriceRepository.findActiveAt(bookId, validFrom);
        List<EditorialPrice> officialSources = eligibleOfficialSources(sources);

        long distinctOfficialValues = officialSources.stream()
                .map(this::toPriceValue)
                .distinct()
                .count();

        if (distinctOfficialValues <= 1) {
            throw new BusinessException("El libro no tiene un conflicto de precio oficial pendiente para esa vigencia.");
        }

        EditorialPrice selectedPrice;
        EditorialPriceResolutionType resolutionType;

        if (hasSelectedPrice) {
            selectedPrice = officialSources.stream()
                    .filter(source -> Objects.equals(source.getId(), request.selectedEditorialPriceId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("El precio seleccionado no pertenece a las fuentes oficiales en conflicto."));

            resolutionType = EditorialPriceResolutionType.SOURCE_SELECTION;
        } else {
            ManualEditorialPriceRequest manualRequest = request.manualPrice();

            if (manualRequest.validFrom() == null || !manualRequest.validFrom().equals(validFrom)) {
                throw new BusinessException("El precio manual debe tener la misma fecha de vigencia que el conflicto.");
            }

            selectedPrice = createManualPriceInternal(bookId, manualRequest, username);
            resolutionType = EditorialPriceResolutionType.MANUAL_OVERRIDE;
        }

        EditorialPriceResolution previousResolution =
                resolutionRepository.findByBookIdAndValidFromAndActiveTrue(bookId, validFrom).orElse(null);

        createResolution(
                book,
                validFrom,
                selectedPrice,
                resolutionType,
                request.note(),
                username,
                previousResolution
        );

        EffectiveEditorialPriceRefreshResult refresh = effectivePriceService.refreshForBooks(Set.of(bookId), validFrom);
        syncDownstream(refresh.changedBookIds());

        editorialPriceHealthCacheService.evictSummaryAfterCommit();
    }

    @Override
    @Transactional
    public void replaceResolution(
            Long resolutionId,
            EditorialPriceConflictResolutionRequest request,
            String username
    ) {
        if (request == null) throw new BusinessException("Debe especificarse la resolución.");

        EditorialPriceResolution current = resolutionRepository.findById(resolutionId)
                .orElseThrow(() -> new BusinessException("No se encontró la resolución."));

        if (!current.isActive()) {
            throw new BusinessException("Solo puede modificarse una resolución activa.");
        }

        boolean hasSelectedPrice = request.selectedEditorialPriceId() != null;
        boolean hasManualPrice = request.manualPrice() != null;

        if (hasSelectedPrice == hasManualPrice) {
            throw new BusinessException("Debe seleccionar una fuente existente o cargar un precio manual, pero no ambos.");
        }

        Long bookId = current.getBook().getId();
        LocalDate validFrom = current.getValidFrom();

        List<EditorialPrice> sources = editorialPriceRepository.findActiveAt(bookId, validFrom);
        List<EditorialPrice> officialSources = eligibleOfficialSources(sources);

        EditorialPrice selectedPrice;
        EditorialPriceResolutionType resolutionType;

        if (hasSelectedPrice) {
            selectedPrice = officialSources.stream()
                    .filter(source -> Objects.equals(source.getId(), request.selectedEditorialPriceId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("El precio seleccionado no pertenece a las fuentes oficiales de esta vigencia."));

            resolutionType = EditorialPriceResolutionType.SOURCE_SELECTION;
        } else {
            ManualEditorialPriceRequest manualRequest = request.manualPrice();

            if (manualRequest.validFrom() == null || !manualRequest.validFrom().equals(validFrom)) {
                throw new BusinessException("El precio manual debe tener la misma fecha de vigencia que la resolución.");
            }

            selectedPrice = createManualPriceInternal(bookId, manualRequest, username);
            resolutionType = EditorialPriceResolutionType.MANUAL_OVERRIDE;
        }

        createResolution(
                current.getBook(),
                validFrom,
                selectedPrice,
                resolutionType,
                request.note(),
                username,
                current
        );

        EffectiveEditorialPriceRefreshResult refresh =
                effectivePriceService.refreshForBooks(Set.of(bookId), validFrom);

        syncDownstream(refresh.changedBookIds());
        editorialPriceHealthCacheService.evictSummaryAfterCommit();
    }

    @Override
    @Transactional
    public void deactivateResolution(Long resolutionId, String note, String username) {
        EditorialPriceResolution resolution = resolutionRepository.findById(resolutionId)
                .orElseThrow(() -> new BusinessException("No se encontró la resolución."));

        if (!resolution.isActive()) {
            throw new BusinessException("La resolución ya se encuentra inactiva.");
        }

        resolution.setActive(false);
        resolution.setDeactivatedAt(Instant.now());
        resolution.setDeactivatedByUsername(username);
        resolution.setDeactivationNote(normalizeNullable(note));

        resolutionRepository.saveAndFlush(resolution);

        Long bookId = resolution.getBook().getId();
        EffectiveEditorialPriceRefreshResult refresh =
                effectivePriceService.refreshForBooks(Set.of(bookId), resolution.getValidFrom());

        syncDownstream(refresh.changedBookIds());
        editorialPriceHealthCacheService.evictSummaryAfterCommit();
    }

    @Override
    @Transactional
    public void updatePriceMetadata(Long editorialPriceId, EditorialPriceMetadataUpdateRequest request) {
        if (request == null) throw new BusinessException("Debe especificar los datos a modificar.");

        EditorialPrice price = editorialPriceRepository.findById(editorialPriceId)
                .orElseThrow(() -> new BusinessException("No se encontró el precio editorial."));

        if (!price.isActive()) throw new BusinessException("No puede modificarse un precio inactivo.");

        if (price.getOrigin() != EditorialPriceOrigin.MANUAL_EXTERNAL) {
            throw new BusinessException("Solo pueden modificarse los datos de una referencia externa.");
        }

        if (request.externalSourceType() == null) {
            throw new BusinessException("Debe indicar el tipo de fuente externa.");
        }

        if (isBlank(request.sourceName())) {
            throw new BusinessException("Debe indicar el nombre de la fuente externa.");
        }

        price.setExternalSourceType(request.externalSourceType());
        price.setSourceName(normalizeNullable(request.sourceName()));
        price.setSourceUrl(normalizeNullable(request.sourceUrl()));
        price.setSourceNote(normalizeNullable(request.note()));

        editorialPriceRepository.save(price);
    }

    @Override
    @Transactional
    public void deactivatePrice(Long editorialPriceId, String note, String username) {
        EditorialPrice price = editorialPriceRepository.findById(editorialPriceId)
                .orElseThrow(() -> new BusinessException("No se encontró el precio editorial."));

        if (!price.isActive()) throw new BusinessException("El precio ya se encuentra inactivo.");

        if (price.getOrigin() == EditorialPriceOrigin.PRICE_LIST) {
            throw new BusinessException("Los precios provenientes de listas no pueden darse de baja manualmente.");
        }

        if (resolutionRepository.existsBySelectedEditorialPriceIdAndActiveTrue(editorialPriceId)) {
            throw new BusinessException(
                    "El precio está siendo utilizado por una resolución activa. Modifique o dé de baja primero esa resolución."
            );
        }

        price.setActive(false);
        price.setDeactivatedAt(Instant.now());
        price.setDeactivatedByUsername(username);
        price.setDeactivationNote(normalizeNullable(note));

        editorialPriceRepository.saveAndFlush(price);

        EffectiveEditorialPriceRefreshResult refresh =
                effectivePriceService.refreshForBooks(Set.of(price.getBook().getId()), price.getValidFrom());

        syncDownstream(refresh.changedBookIds());
        editorialPriceHealthCacheService.evictSummaryAfterCommit();
    }

    private EditorialPrice createManualPriceInternal(
            Long bookId,
            ManualEditorialPriceRequest request,
            String username
    ) {
        validateManualPriceRequest(request);

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BusinessException("No se encontró el libro."));

        PriceListProvider provider = resolveManualProvider(request);

        if (request.origin() == EditorialPriceOrigin.MANUAL_DISTRIBUTOR) {
            if (provider == null) throw new IllegalStateException("El distribuidor resuelto no puede ser nulo.");

            boolean alreadyExists = editorialPriceRepository.findByBookIdAndProviderIdAndValidFromAndOrigin(
                    bookId,
                    provider.getId(),
                    request.validFrom(),
                    EditorialPriceOrigin.MANUAL_DISTRIBUTOR
            ).isPresent();

            if (alreadyExists) {
                throw new BusinessException("Ya existe un precio manual para este distribuidor y vigencia.");
            }
        }

        EditorialPrice price =
                EditorialPrice.builder()
                        .book(book)
                        .price(request.price())
                        .currency("ARS")
                        .provider(provider)
                        .validFrom(request.validFrom())
                        .active(true)
                        .origin(request.origin())
                        .externalSourceType(request.externalSourceType())
                        .sourceName(normalizeNullable(request.sourceName()))
                        .sourceUrl(normalizeNullable(request.sourceUrl()))
                        .sourceNote(normalizeNullable(request.note()))
                        .createdByUsername(username)
                        .build();

        price = editorialPriceRepository.saveAndFlush(price);

        EditorialPriceConfirmation confirmation =
                buildInitialConfirmation(
                        price,
                        request,
                        username
                );

        confirmationRepository.save(confirmation);

        return price;
    }

    private EditorialPriceConfirmation buildInitialConfirmation(
            EditorialPrice price,
            ManualEditorialPriceRequest request,
            String username
    ) {
        EditorialPriceConfirmationSourceType sourceType =
                switch (request.origin()) {
                    case MANUAL_DISTRIBUTOR -> EditorialPriceConfirmationSourceType.DISTRIBUTOR;
                    case MANUAL_PUBLISHER -> EditorialPriceConfirmationSourceType.PUBLISHER;
                    case MANUAL_EXTERNAL -> EditorialPriceConfirmationSourceType.EXTERNAL;
                    case PRICE_LIST -> throw new IllegalStateException("PRICE_LIST no es una carga manual.");
                };

        return EditorialPriceConfirmation.builder()
                .editorialPrice(price)
                .confirmedOn(request.confirmedOn())
                .sourceType(sourceType)
                .provider(price.getProvider())
                .externalSourceType(request.externalSourceType())
                .sourceName(normalizeNullable(request.sourceName()))
                .sourceUrl(normalizeNullable(request.sourceUrl()))
                .note(normalizeNullable(request.note()))
                .createdByUsername(username)
                .build();
    }

    private void validateManualPriceRequest(
            ManualEditorialPriceRequest request
    ) {
        if (request == null) {
            throw new BusinessException("Debe especificarse el precio.");
        }

        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("El precio debe ser mayor a cero.");
        }

        if (request.validFrom() == null) {
            throw new BusinessException("La fecha de vigencia es obligatoria.");
        }

        if (request.confirmedOn() == null) {
            throw new BusinessException("La fecha de confirmación es obligatoria.");
        }

        if (request.confirmedOn().isAfter(LocalDate.now(ANAQUEL_ZONE))) {
            throw new BusinessException("La fecha de confirmación no puede ser futura.");
        }

        if (request.origin() == null) {
            throw new BusinessException("Debe indicarse el origen del precio.");
        }

        if (request.origin() == EditorialPriceOrigin.PRICE_LIST) {
            throw new BusinessException("PRICE_LIST solo puede generarse mediante una importación.");
        }

        switch (request.origin()) {
            case MANUAL_DISTRIBUTOR -> {
                if (request.providerId() == null) {
                    throw new BusinessException("Debe seleccionar el distribuidor.");
                }
            }

            case MANUAL_PUBLISHER -> {
                if (isBlank(request.sourceName())) {
                    throw new BusinessException("Debe indicar la editorial consultada.");
                }
            }

            case MANUAL_EXTERNAL -> {
                if (request.externalSourceType() == null) {
                    throw new BusinessException("Debe indicar el tipo de fuente externa.");
                }

                if (isBlank(request.sourceName())) {
                    throw new BusinessException("Debe indicar el nombre de la fuente externa.");
                }
            }
        }
    }

    private void validateConfirmation(EditorialPriceConfirmationRequest request) {
        if (request.confirmedOn() == null) {
            throw new BusinessException("La fecha de confirmación es obligatoria.");
        }

        if (request.confirmedOn().isAfter(LocalDate.now(ANAQUEL_ZONE))) {
            throw new BusinessException("La fecha de confirmación no puede ser futura.");
        }

        if (request.sourceType() == null) {
            throw new BusinessException("Debe indicarse la fuente de confirmación.");
        }

        switch (request.sourceType()) {
            case DISTRIBUTOR -> {
                if (request.providerId() == null) {
                    throw new BusinessException("Debe seleccionar el distribuidor.");
                }
            }

            case PUBLISHER -> {
                if (isBlank(request.sourceName())) {
                    throw new BusinessException("Debe indicar la editorial consultada.");
                }
            }

            case EXTERNAL -> {
                if (request.externalSourceType() == null) {
                    throw new BusinessException("Debe indicar el tipo de fuente externa.");
                }

                if (isBlank(request.sourceName())) {
                    throw new BusinessException("Debe indicar el nombre de la fuente externa.");
                }
            }
        }
    }

    private PriceListProvider resolveManualProvider(ManualEditorialPriceRequest request) {
        if (request.origin() != EditorialPriceOrigin.MANUAL_DISTRIBUTOR) {
            return null;
        }

        return providerRepository
                .findById(request.providerId())
                .filter(PriceListProvider::isActive)
                .orElseThrow(() -> new BusinessException("No se encontró el distribuidor seleccionado."));
    }

    private PriceListProvider resolveConfirmationProvider(EditorialPriceConfirmationRequest request) {
        if (request.sourceType() != EditorialPriceConfirmationSourceType.DISTRIBUTOR) {
            return null;
        }

        return providerRepository
                .findById(request.providerId())
                .filter(PriceListProvider::isActive)
                .orElseThrow(() -> new BusinessException("No se encontró el distribuidor seleccionado."));
    }

    private boolean isOfficial(EditorialPrice price) {
        return price.getOrigin().isOfficial();
    }

    private void syncDownstream(Set<Long> changedBookIds) {
        if (changedBookIds.isEmpty()) {
            return;
        }

        var syncResult =
                inventoryEditorialPriceSyncRepository
                        .syncCurrentPrices(
                                changedBookIds,
                                LocalDate.now(
                                        ANAQUEL_ZONE
                                )
                        );

        if (!syncResult.tiendanubeSyncInventoryIds().isEmpty()) {
            eventPublisher.publishEvent(
                    new TiendanubePriceSyncRequestedEvent(
                            syncResult.tiendanubeSyncInventoryIds()
                    )
            );
        }
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<EditorialPrice> eligibleOfficialSources(List<EditorialPrice> sources) {
        return sources.stream()
                .filter(this::isOfficial)
                .filter(source -> !isShadowedPriceList(source, sources))
                .toList();
    }

    private boolean isShadowedPriceList(EditorialPrice source, List<EditorialPrice> sources) {
        if (source.getOrigin() != EditorialPriceOrigin.PRICE_LIST || source.getProvider() == null) return false;

        Long providerId = source.getProvider().getId();

        return sources.stream().anyMatch(other ->
                other.getOrigin() == EditorialPriceOrigin.MANUAL_DISTRIBUTOR
                        && other.getProvider() != null
                        && Objects.equals(other.getProvider().getId(), providerId)
        );
    }

    private PriceValue toPriceValue(EditorialPrice price) {
        return new PriceValue(
                price.getPrice().stripTrailingZeros(),
                normalizeCurrency(price.getCurrency())
        );
    }

    private String normalizeCurrency(String currency) {
        return currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
    }

    private record PriceValue(BigDecimal price, String currency) {
    }

    private EditorialPriceResolution createResolution(
            Book book,
            LocalDate validFrom,
            EditorialPrice selectedPrice,
            EditorialPriceResolutionType resolutionType,
            String note,
            String username,
            EditorialPriceResolution previousResolution
    ) {
        if (previousResolution != null) {
            previousResolution.setActive(false);
            resolutionRepository.save(previousResolution);
            resolutionRepository.flush();
        }

        EditorialPriceResolution resolution = EditorialPriceResolution.builder()
                .book(book)
                .validFrom(validFrom)
                .selectedEditorialPrice(selectedPrice)
                .resolvedPrice(selectedPrice.getPrice())
                .resolvedCurrency(normalizeCurrency(selectedPrice.getCurrency()))
                .resolutionType(resolutionType)
                .note(normalizeNullable(note))
                .resolvedByUsername(username)
                .active(true)
                .supersedesResolution(previousResolution)
                .build();

        return resolutionRepository.saveAndFlush(resolution);
    }
}