package com.rodrilang.librarymanager.importer.price.configuration.controller;

import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListWorkbookAnalysisResponse;
import com.rodrilang.librarymanager.importer.price.configuration.dto.request.CreatePriceListImportConfigRequest;
import com.rodrilang.librarymanager.importer.price.configuration.dto.response.PriceListImportConfigResponse;
import com.rodrilang.librarymanager.importer.price.configuration.service.PriceListImportConfigService;
import com.rodrilang.librarymanager.importer.price.configuration.service.PriceListWorkbookAnalyzer;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(
        name = "Listas de precios",
        description = "Configuración y análisis de listas de precios de proveedores"
)
@RestController
@RequestMapping("/api/price-list-imports")
@RequiredArgsConstructor
public class PriceListImportConfigurationController {

    private final PriceListImportConfigService configService;
    private final PriceListWorkbookAnalyzer workbookAnalyzer;

    @PostMapping("/providers/{providerId}/config")
    public ResponseEntity<PriceListImportConfigResponse> createConfig(
            @PathVariable Long providerId,
            @Valid @RequestBody CreatePriceListImportConfigRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configService.create(providerId, request));
    }

    @GetMapping("/providers/{providerId}/config")
    public ResponseEntity<PriceListImportConfigResponse> getActiveConfig(@PathVariable Long providerId) {
        return ResponseEntity.ok(configService.findActiveByProvider(providerId));
    }

    @PostMapping(value = "/analyze-template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PriceListWorkbookAnalysisResponse> analyzeTemplate(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(workbookAnalyzer.analyze(file));
    }
}
