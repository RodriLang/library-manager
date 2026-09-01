package com.rodrilang.librarymanager.editorialprice.service.impl;

import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceBookDetailResponse;
import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceConfirmationResponse;
import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceResolutionResponse;
import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceSourceResponse;
import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceValidityResponse;
import com.rodrilang.librarymanager.editorialprice.dto.response.EffectiveEditorialPriceDetailResponse;
import com.rodrilang.librarymanager.editorialprice.dto.response.EffectiveEditorialPriceHistoryResponse;
import com.rodrilang.librarymanager.editorialprice.model.EditorialPriceConfirmation;
import com.rodrilang.librarymanager.editorialprice.model.EditorialPriceResolution;
import com.rodrilang.librarymanager.editorialprice.model.EffectiveEditorialPrice;
import com.rodrilang.librarymanager.editorialprice.repository.EditorialPriceConfirmationRepository;
import com.rodrilang.librarymanager.editorialprice.repository.EditorialPriceResolutionRepository;
import com.rodrilang.librarymanager.editorialprice.repository.EffectiveEditorialPriceRepository;
import com.rodrilang.librarymanager.editorialprice.service.EditorialPriceQueryService;
import com.rodrilang.librarymanager.editorialprice.service.EffectiveEditorialPriceService;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.EditorialPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EditorialPriceQueryServiceImpl implements EditorialPriceQueryService {

    private final BookRepository bookRepository;
    private final EditorialPriceRepository editorialPriceRepository;
    private final EffectiveEditorialPriceRepository effectiveEditorialPriceRepository;
    private final EditorialPriceResolutionRepository resolutionRepository;
    private final EditorialPriceConfirmationRepository confirmationRepository;
    private final EffectiveEditorialPriceService effectiveEditorialPriceService;

    @Override
    @Transactional(readOnly = true)
    public EditorialPriceBookDetailResponse getBookDetail(Long bookId) {
        if (!bookRepository.existsById(bookId))
            throw new ResourceNotFoundException("No se encontró el libro con ID: " + bookId);

        List<EditorialPrice> sources =
                editorialPriceRepository.findByBookIdAndActiveTrueOrderByValidFromDescIdDesc(bookId);

        List<EffectiveEditorialPrice> effectiveHistory =
                effectiveEditorialPriceRepository.findByBookIdAndActiveTrueOrderByValidFromDescIdDesc(bookId);

        List<EditorialPriceResolution> resolutions =
                resolutionRepository.findByBookIdAndActiveTrueOrderByValidFromDescIdDesc(bookId);

        Map<Long, List<EditorialPriceConfirmation>> confirmationsBySource = loadConfirmations(sources);

        Map<LocalDate, EditorialPriceResolution> resolutionByDate =
                resolutions.stream().collect(LinkedHashMap::new,
                        (map, resolution)
                                -> map.putIfAbsent(
                                resolution.getValidFrom(), resolution),
                        Map::putAll
                );

        Map<LocalDate, List<EditorialPrice>> sourcesByDate = new LinkedHashMap<>();

        for (EditorialPrice source : sources) {
            sourcesByDate.computeIfAbsent(source.getValidFrom(), ignored -> new ArrayList<>()).add(source);
        }

        List<EditorialPriceValidityResponse> validities = sourcesByDate.entrySet().stream()
                .map(entry -> toValidityResponse(
                        entry.getKey(), entry.getValue(), resolutionByDate.get(entry.getKey()), confirmationsBySource))
                .toList();

        EffectiveEditorialPrice current = effectiveEditorialPriceService.findCurrentByBookId(bookId).orElse(null);

        return new EditorialPriceBookDetailResponse(
                current != null ? toCurrentResponse(current) : null,
                validities,
                effectiveHistory.stream().map(this::toHistoryResponse).toList()
        );
    }

    private Map<Long, List<EditorialPriceConfirmation>> loadConfirmations(List<EditorialPrice> sources) {
        if (sources.isEmpty()) return Map.of();

        List<Long> ids = sources.stream().map(EditorialPrice::getId).toList();
        List<EditorialPriceConfirmation> confirmations =
                confirmationRepository.findByEditorialPriceIdInOrderByConfirmedOnDescIdDesc(ids);

        Map<Long, List<EditorialPriceConfirmation>> result = new HashMap<>();
        for (EditorialPriceConfirmation confirmation : confirmations) {
            result.computeIfAbsent(
                    confirmation.getEditorialPrice().getId(), ignored -> new ArrayList<>()).add(confirmation);
        }

        return result;
    }

    private EditorialPriceValidityResponse toValidityResponse(
            LocalDate validFrom,
            List<EditorialPrice> sources,
            EditorialPriceResolution resolution,
            Map<Long, List<EditorialPriceConfirmation>> confirmationsBySource
    ) {
        boolean rawOfficialConflict = hasOfficialConflict(sources);
        boolean unresolvedConflict = rawOfficialConflict && resolution == null;

        List<EditorialPriceSourceResponse> sourceResponses = sources.stream()
                .map(source -> toSourceResponse(
                        source, confirmationsBySource.getOrDefault(source.getId(), List.of())))
                .toList();

        return new EditorialPriceValidityResponse(
                validFrom,
                unresolvedConflict,
                sourceResponses,
                resolution != null ? toResolutionResponse(resolution) : null
        );
    }

    private boolean hasOfficialConflict(Collection<EditorialPrice> sources) {
        return sources.stream()
                .filter(source -> source.getOrigin().isOfficial())
                .map(source -> new PriceKey(
                        normalizePrice(source.getPrice()), normalizeCurrency(source.getCurrency())))
                .distinct()
                .limit(2)
                .count() > 1;
    }

    private EditorialPriceSourceResponse toSourceResponse(
            EditorialPrice source,
            List<EditorialPriceConfirmation> confirmations
    ) {
        LocalDate lastConfirmedOn = confirmations.stream()
                .map(EditorialPriceConfirmation::getConfirmedOn)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new EditorialPriceSourceResponse(
                source.getId(),
                source.getPrice(),
                source.getCurrency(),
                source.getValidFrom(),
                source.getOrigin(),
                source.getProvider() != null ? source.getProvider().getId() : null,
                source.getProvider() != null ? source.getProvider().getName() : null,
                source.getProvider() != null ? source.getProvider().getCode() : null,
                source.getExternalSourceType(),
                source.getSourceName(),
                source.getSourceUrl(),
                source.getSourceNote(),
                source.getCreatedByUsername(),
                lastConfirmedOn,
                confirmations.stream().map(this::toConfirmationResponse).toList()
        );
    }

    private EditorialPriceConfirmationResponse toConfirmationResponse(EditorialPriceConfirmation confirmation) {
        return new EditorialPriceConfirmationResponse(
                confirmation.getId(),
                confirmation.getConfirmedOn(),
                confirmation.getSourceType(),
                confirmation.getProvider() != null ? confirmation.getProvider().getId() : null,
                confirmation.getProvider() != null ? confirmation.getProvider().getName() : null,
                confirmation.getExternalSourceType(),
                confirmation.getSourceName(),
                confirmation.getSourceUrl(),
                confirmation.getNote(),
                confirmation.getCreatedByUsername()
        );
    }

    private EditorialPriceResolutionResponse toResolutionResponse(EditorialPriceResolution resolution) {
        return new EditorialPriceResolutionResponse(
                resolution.getId(),
                resolution.getValidFrom(),
                resolution.getSelectedEditorialPrice() != null ? resolution.getSelectedEditorialPrice().getId() : null,
                resolution.getResolvedPrice(),
                resolution.getResolutionType(),
                resolution.getNote(),
                resolution.getResolvedByUsername()
        );
    }

    private EffectiveEditorialPriceDetailResponse toCurrentResponse(EffectiveEditorialPrice price) {
        return new EffectiveEditorialPriceDetailResponse(
                price.getId(),
                price.getPrice(),
                price.getCurrency(),
                price.getValidFrom(),
                price.getDeterminationType(),
                price.getAuthority()
        );
    }

    private EffectiveEditorialPriceHistoryResponse toHistoryResponse(EffectiveEditorialPrice price) {
        return new EffectiveEditorialPriceHistoryResponse(
                price.getId(),
                price.getPrice(),
                price.getCurrency(),
                price.getValidFrom(),
                price.getDeterminationType(),
                price.getAuthority(),
                price.getSelectedEditorialPrice() != null ? price.getSelectedEditorialPrice().getId() : null,
                price.getResolution() != null ? price.getResolution().getId() : null
        );
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        return price == null ? null : price.stripTrailingZeros();
    }

    private String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    private record PriceKey(BigDecimal price, String currency) {
    }
}