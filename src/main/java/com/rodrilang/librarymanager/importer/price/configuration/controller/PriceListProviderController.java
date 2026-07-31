package com.rodrilang.librarymanager.importer.price.configuration.controller;

import com.rodrilang.librarymanager.importer.price.configuration.dto.CreatePriceListImportConfigRequest;
import com.rodrilang.librarymanager.importer.price.configuration.dto.CreatePriceListProviderRequest;
import com.rodrilang.librarymanager.importer.price.configuration.dto.PriceListImportConfigResponse;
import com.rodrilang.librarymanager.importer.price.configuration.dto.PriceListProviderResponse;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListWorkbookAnalysisResponse;
import com.rodrilang.librarymanager.importer.price.configuration.service.PriceListImportConfigService;
import com.rodrilang.librarymanager.importer.price.configuration.service.PriceListProviderService;
import com.rodrilang.librarymanager.importer.price.configuration.service.PriceListWorkbookAnalyzer;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(
        name = "Proveedores de listas de precios",
        description = "Configuración de proveedores y formatos de listas de precios"
)
@RestController
@RequestMapping("/api/price-list-providers")
@RequiredArgsConstructor
public class PriceListProviderController {

    private final PriceListProviderService providerService;
    private final PriceListImportConfigService configService;
    private final PriceListWorkbookAnalyzer workbookAnalyzer;

    @PostMapping
    public ResponseEntity<PriceListProviderResponse> create(@Valid @RequestBody CreatePriceListProviderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(providerService.create(request));
    }

    @PostMapping("/{providerId}/config")
    public ResponseEntity<PriceListImportConfigResponse> createConfig(
            @PathVariable Long providerId,
            @Valid @RequestBody CreatePriceListImportConfigRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configService.create(providerId, request));
    }

    @PostMapping(value = "/analyze-template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PriceListWorkbookAnalysisResponse> analyzeTemplate(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(workbookAnalyzer.analyze(file));
    }

    @GetMapping("/{providerId}/config")
    public ResponseEntity<PriceListImportConfigResponse> getActiveConfig(@PathVariable Long providerId) {
        return ResponseEntity.ok(configService.findActiveByProvider(providerId));
    }

    @GetMapping
    public ResponseEntity<List<PriceListProviderResponse>> findAll() {
        return ResponseEntity.ok(providerService.findAllActive());
    }

    @GetMapping("/{providerId}")
    public ResponseEntity<PriceListProviderResponse> findById(@PathVariable Long providerId) {
        return ResponseEntity.ok(providerService.findById(providerId));
    }
}