package com.rodrilang.librarymanager.importer.price.configuration.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.dto.CreatePriceListImportConfigRequest;
import com.rodrilang.librarymanager.importer.price.configuration.dto.PriceListColumnMappingRequest;
import com.rodrilang.librarymanager.importer.price.configuration.dto.PriceListColumnMappingResponse;
import com.rodrilang.librarymanager.importer.price.configuration.dto.PriceListImportConfigResponse;
import com.rodrilang.librarymanager.importer.price.configuration.enums.HeaderStrategy;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListField;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListColumnMapping;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.configuration.repository.PriceListImportConfigRepository;
import com.rodrilang.librarymanager.importer.price.configuration.repository.PriceListProviderRepository;
import com.rodrilang.librarymanager.importer.price.configuration.service.PriceListImportConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PriceListImportConfigServiceImpl implements PriceListImportConfigService {

    private final PriceListProviderRepository providerRepository;
    private final PriceListImportConfigRepository configRepository;

    @Override
    @Transactional
    public PriceListImportConfigResponse create(Long providerId, CreatePriceListImportConfigRequest request) {
        PriceListProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new BusinessException("No se encontró el proveedor solicitado."));

        if (!provider.isActive()) {
            throw new BusinessException("El proveedor se encuentra inactivo.");
        }

        validateConfiguration(request);

        configRepository
                .findFirstByProviderIdAndActiveTrue(providerId)
                .ifPresent(config -> {
                    throw new BusinessException("El proveedor ya tiene una configuración activa.");
                });

        Instant now = Instant.now();

        PriceListImportConfig config = PriceListImportConfig.builder()
                .provider(provider)
                .name(request.name().trim())
                .sheetStrategy(request.sheetStrategy())
                .sheetIndex(request.sheetIndex())
                .sheetName(normalizeNullable(request.sheetName()))
                .headerStrategy(request.headerStrategy())
                .headerRowIndex(request.headerRowIndex())
                .firstDataRowIndex(request.firstDataRowIndex())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        List<PriceListColumnMapping> mappings = request.mappings()
                .stream()
                .map(mapping -> PriceListColumnMapping.builder()
                        .importConfig(config)
                        .targetField(mapping.targetField())
                        .columnIndex(mapping.columnIndex())
                        .expectedHeader(
                                normalizeNullable(mapping.expectedHeader())
                        )
                        .valueType(mapping.valueType())
                        .required(mapping.required())
                        .active(true)
                        .build()
                )
                .toList();

        config.getMappings().addAll(mappings);

        return toResponse(configRepository.save(config));
    }

    @Override
    @Transactional(readOnly = true)
    public PriceListImportConfigResponse findActiveByProvider(Long providerId) {
        return configRepository
                .findFirstByProviderIdAndActiveTrue(providerId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException("El proveedor no tiene una configuración activa."));
    }

    private void validateConfiguration(CreatePriceListImportConfigRequest request) {
        validateSheetConfiguration(request);
        validateHeaderConfiguration(request);
        validateRows(request);
        validateMappings(request.mappings());
    }

    private void validateSheetConfiguration(CreatePriceListImportConfigRequest request) {
        switch (request.sheetStrategy()) {

            case FIRST -> {
                if (request.sheetIndex() != null
                        || hasText(request.sheetName())) {
                    throw new BusinessException("La estrategia FIRST no permite indicar hoja.");
                }
            }

            case BY_INDEX -> {
                if (request.sheetIndex() == null) {
                    throw new BusinessException("Debe indicar el índice de la hoja.");
                }
            }

            case BY_NAME, NAME_CONTAINS -> {
                if (!hasText(request.sheetName())) {
                    throw new BusinessException("Debe indicar el nombre de la hoja.");
                }
            }
        }
    }

    private void validateHeaderConfiguration(CreatePriceListImportConfigRequest request) {
        if (request.headerStrategy() == HeaderStrategy.NONE && request.headerRowIndex() != null) {
            throw new BusinessException("Una configuración sin encabezado no puede indicar fila de encabezado.");
        }

        if (request.headerStrategy() == HeaderStrategy.FIXED_ROW && request.headerRowIndex() == null) {
            throw new BusinessException("Debe indicar la fila de encabezado.");
        }
    }

    private void validateRows(CreatePriceListImportConfigRequest request) {
        if (request.headerRowIndex() != null && request.firstDataRowIndex() <= request.headerRowIndex()) {
            throw new BusinessException("La primera fila de datos debe estar después del encabezado.");
        }
    }

    private void validateMappings(List<PriceListColumnMappingRequest> mappings) {
        Set<PriceListField> fields = new HashSet<>();

        for (PriceListColumnMappingRequest mapping : mappings) {
            if (!fields.add(mapping.targetField())) {
                throw new BusinessException("El campo " + mapping.targetField() + " está mapeado más de una vez.");
            }
        }

        requireField(fields, PriceListField.TITLE);
        requireField(fields, PriceListField.RETAIL_PRICE);
    }

    private void requireField(Set<PriceListField> fields, PriceListField field) {
        if (!fields.contains(field)) {
            throw new BusinessException("La configuración debe incluir el campo " + field + ".");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeNullable(String value) {
        return hasText(value)
                ? value.trim()
                : null;
    }

    private PriceListImportConfigResponse toResponse(PriceListImportConfig config) {
        return new PriceListImportConfigResponse(
                config.getId(),
                config.getProvider().getId(),
                config.getName(),
                config.getSheetStrategy(),
                config.getSheetIndex(),
                config.getSheetName(),
                config.getHeaderStrategy(),
                config.getHeaderRowIndex(),
                config.getFirstDataRowIndex(),
                config.isActive(),
                config.getMappings()
                        .stream()
                        .map(mapping ->
                                new PriceListColumnMappingResponse(
                                        mapping.getId(),
                                        mapping.getTargetField(),
                                        mapping.getColumnIndex(),
                                        mapping.getExpectedHeader(),
                                        mapping.getValueType(),
                                        mapping.isRequired(),
                                        mapping.isActive()
                                )
                        )
                        .toList()
        );
    }
}