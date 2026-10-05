package com.rodrilang.librarymanager.admin.catalog.importer.service;

import com.rodrilang.librarymanager.admin.catalog.importer.dto.CatalogImportFormatRequest;
import com.rodrilang.librarymanager.admin.catalog.importer.dto.CatalogImportFormatResponse;
import com.rodrilang.librarymanager.admin.catalog.importer.model.CatalogImportFormat;
import com.rodrilang.librarymanager.admin.catalog.importer.repository.CatalogImportFormatRepository;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListColumnMapping;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.model.ProviderType;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogImportFormatService {
    private final CatalogImportFormatRepository repository;
    private final ProviderRepository providerRepository;

    @Transactional(readOnly = true)
    public List<CatalogImportFormatResponse> findAll(Long providerId) {
        List<CatalogImportFormat> formats = providerId == null
                ? repository.findAllByActiveTrueOrderByProviderNameAscNameAsc()
                : repository.findAllByProviderIdAndActiveTrueOrderByNameAsc(providerId);
        return formats.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CatalogImportFormatResponse findById(Long id) { return toResponse(require(id)); }

    @Transactional
    public CatalogImportFormatResponse create(CatalogImportFormatRequest request) {
        Provider provider = requireProvider(request.providerId());
        CatalogImportFormat format = new CatalogImportFormat();
        apply(format, provider, request);
        return toResponse(repository.save(format));
    }

    @Transactional
    public CatalogImportFormatResponse update(Long id, CatalogImportFormatRequest request) {
        CatalogImportFormat format = require(id);
        Provider provider = requireProvider(request.providerId());
        apply(format, provider, request);
        return toResponse(format);
    }

    public PriceListImportConfig toParserConfig(CatalogImportFormat format) {
        return PriceListImportConfig.builder()
                .name(format.getName())
                .sheetStrategy(format.getSheetStrategy())
                .sheetIndex(format.getSheetIndex())
                .sheetName(format.getSheetName())
                .headerStrategy(format.getHeaderStrategy())
                .headerRowIndex(format.getHeaderRowIndex())
                .firstDataRowIndex(format.getFirstDataRowIndex())
                .active(format.isActive())
                .mappings(format.getMappings().stream().map(m -> PriceListColumnMapping.builder()
                        .targetField(m.getTargetField())
                        .columnIndex(m.getColumnIndex())
                        .expectedHeader(m.getExpectedHeader())
                        .valueType(m.getValueType())
                        .required(m.isRequired())
                        .active(m.isActive())
                        .build()).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public CatalogImportFormat require(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("No se encontró el formato de importación solicitado."));
    }

    private Provider requireProvider(Long id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> new BusinessException("No se encontró el proveedor seleccionado."));
        if (!provider.isActive() || provider.getType() != ProviderType.COMMERCIAL) {
            throw new BusinessException("El proveedor seleccionado no está disponible para importación de catálogo.");
        }
        return provider;
    }

    private void apply(CatalogImportFormat format, Provider provider, CatalogImportFormatRequest request) {
        if (request.mappings().stream().noneMatch(m -> m.active() && m.targetField().name().equals("ISBN"))
                && request.mappings().stream().noneMatch(m -> m.active() && m.targetField().name().equals("EXTERNAL_CODE"))) {
            throw new BusinessException("El formato debe mapear ISBN o código externo para identificar libros de forma segura.");
        }
        format.setProvider(provider);
        format.setName(request.name().trim());
        format.setSheetStrategy(request.sheetStrategy());
        format.setSheetIndex(request.sheetIndex());
        format.setSheetName(clean(request.sheetName()));
        format.setHeaderStrategy(request.headerStrategy());
        format.setHeaderRowIndex(request.headerRowIndex());
        format.setFirstDataRowIndex(request.firstDataRowIndex());
        format.setActive(request.active() == null || request.active());
        format.setMappings(request.mappings().stream().map(m -> CatalogImportFormat.Mapping.builder()
                .targetField(m.targetField()).columnIndex(m.columnIndex()).expectedHeader(clean(m.expectedHeader()))
                .valueType(m.valueType()).required(m.required()).active(m.active()).build()).toList());
    }

    private CatalogImportFormatResponse toResponse(CatalogImportFormat f) {
        return new CatalogImportFormatResponse(f.getId(), f.getProvider().getId(), f.getProvider().getName(), f.getName(),
                f.getSheetStrategy(), f.getSheetIndex(), f.getSheetName(), f.getHeaderStrategy(), f.getHeaderRowIndex(),
                f.getFirstDataRowIndex(), f.isActive(), f.getMappings().stream().map(m -> new CatalogImportFormatResponse.MappingResponse(
                m.getTargetField(), m.getColumnIndex(), m.getExpectedHeader(), m.getValueType(), m.isRequired(), m.isActive())).toList());
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
