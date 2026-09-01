package com.rodrilang.librarymanager.editorialprice.service.impl;

import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceResolutionListResponse;
import com.rodrilang.librarymanager.editorialprice.model.EditorialPriceResolution;
import com.rodrilang.librarymanager.editorialprice.repository.EditorialPriceResolutionRepository;
import com.rodrilang.librarymanager.editorialprice.service.EditorialPriceResolutionService;
import com.rodrilang.librarymanager.model.EditorialPrice;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EditorialPriceResolutionServiceImpl implements EditorialPriceResolutionService {

    private final EditorialPriceResolutionRepository resolutionRepository;

    @Override
    public Page<EditorialPriceResolutionListResponse> findAll(Boolean active, String query, Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        Pageable normalizedPageable = normalizePageable(pageable);

        Page<EditorialPriceResolution> resolutions = normalizedQuery == null
                ? resolutionRepository.findAllForList(active, normalizedPageable)
                : resolutionRepository.search(active, normalizedQuery.toLowerCase(Locale.ROOT), normalizedPageable);

        return resolutions.map(this::toResponse);
    }

    private EditorialPriceResolutionListResponse toResponse(EditorialPriceResolution resolution) {
        EditorialPrice selectedPrice = resolution.getSelectedEditorialPrice();

        return new EditorialPriceResolutionListResponse(
                resolution.getId(),
                resolution.getBook().getId(),
                resolution.getBook().getTitle(),
                resolution.getBook().getPreferredIsbn(),
                resolution.getValidFrom(),
                selectedPrice.getId(),
                resolution.getResolvedPrice(),
                resolution.getResolvedCurrency(),
                resolution.getResolutionType(),
                resolveSourceName(selectedPrice),
                resolution.getNote(),
                resolution.getResolvedByUsername(),
                resolution.getCreatedAt(),
                resolution.isActive(),
                resolution.getDeactivatedAt(),
                resolution.getDeactivatedByUsername(),
                resolution.getDeactivationNote()
        );
    }

    private String resolveSourceName(EditorialPrice price) {
        if (price.getSourceName() != null && !price.getSourceName().isBlank()) return price.getSourceName();
        return price.getProvider() != null ? price.getProvider().getName() : null;
    }

    private String normalizeQuery(String query) {
        if (query == null) return null;
        String normalized = query.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Pageable normalizePageable(Pageable pageable) {
        if (pageable.getSort().isSorted()) return pageable;

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
    }
}