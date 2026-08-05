package com.rodrilang.librarymanager.importer.price.configuration.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.dto.request.CreatePriceListProviderRequest;
import com.rodrilang.librarymanager.importer.price.configuration.dto.response.PriceListProviderResponse;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.configuration.repository.PriceListProviderRepository;
import com.rodrilang.librarymanager.importer.price.configuration.service.PriceListProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PriceListProviderServiceImpl implements PriceListProviderService {

    private final PriceListProviderRepository providerRepository;

    @Override
    @Transactional
    public PriceListProviderResponse create(CreatePriceListProviderRequest request) {
        String code = normalizeCode(request.code());

        if (providerRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Ya existe un proveedor con el código " + code + ".");
        }

        Instant now = Instant.now();

        PriceListProvider provider = PriceListProvider.builder()
                .code(code)
                .name(request.name().trim())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toResponse(providerRepository.save(provider));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceListProviderResponse> findAllActive() {
        return providerRepository
                .findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PriceListProviderResponse findById(Long id) {
        PriceListProvider provider = providerRepository.findById(id)
                .orElseThrow(() -> new BusinessException("No se encontró el proveedor solicitado."));

        return toResponse(provider);
    }

    private String normalizeCode(String value) {
        return value
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private PriceListProviderResponse toResponse(PriceListProvider provider) {
        return new PriceListProviderResponse(
                provider.getId(),
                provider.getCode(),
                provider.getName(),
                provider.isActive()
        );
    }
}