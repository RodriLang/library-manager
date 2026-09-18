package com.rodrilang.librarymanager.provider.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.provider.dto.request.CreateProviderRequest;
import com.rodrilang.librarymanager.provider.dto.request.UpdateProviderRequest;
import com.rodrilang.librarymanager.provider.dto.response.ProviderResponse;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.model.ProviderType;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import com.rodrilang.librarymanager.provider.service.ProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    private final ProviderRepository providerRepository;

    @Override
    @Transactional
    public ProviderResponse create(CreateProviderRequest request) {
        String code = normalizeCode(request.code());

        if (providerRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Ya existe un proveedor con el código " + code + ".");
        }

        Instant now = Instant.now();

        Provider provider = Provider.builder()
                .code(code)
                .name(request.name().trim())
                .type(ProviderType.COMMERCIAL)
                .taxId(clean(request.taxId()))
                .email(clean(request.email()))
                .phone(clean(request.phone()))
                .notes(clean(request.notes()))
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toResponse(providerRepository.save(provider));
    }

    @Override
    @Transactional
    public ProviderResponse update(Long id, UpdateProviderRequest request) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> new BusinessException("No se encontró el proveedor solicitado."));

        if (provider.getType() == ProviderType.SYSTEM) {
            throw new BusinessException("Los proveedores internos del sistema no se pueden editar desde esta operación.");
        }

        provider.setName(request.name().trim());
        provider.setTaxId(clean(request.taxId()));
        provider.setEmail(clean(request.email()));
        provider.setPhone(clean(request.phone()));
        provider.setNotes(clean(request.notes()));
        if (request.active() != null) provider.setActive(request.active());
        provider.setUpdatedAt(Instant.now());

        return toResponse(provider);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderResponse> findAllActive(ProviderType type) {
        List<Provider> providers = type == null
                ? providerRepository.findAllByActiveTrueOrderByNameAsc()
                : providerRepository.findAllByActiveTrueAndTypeOrderByNameAsc(type);

        return providers.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderResponse findById(Long id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> new BusinessException("No se encontró el proveedor solicitado."));

        return toResponse(provider);
    }

    private ProviderResponse toResponse(Provider provider) {
        return new ProviderResponse(
                provider.getId(),
                provider.getCode(),
                provider.getName(),
                provider.getType(),
                provider.getTaxId(),
                provider.getEmail(),
                provider.getPhone(),
                provider.getNotes(),
                provider.isActive(),
                provider.isPurchasable()
        );
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeCode(String value) {
        return value
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
