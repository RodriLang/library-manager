package com.rodrilang.librarymanager.admin.catalog.importer.controller;

import com.rodrilang.librarymanager.admin.catalog.importer.dto.*;
import com.rodrilang.librarymanager.admin.catalog.importer.service.*;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListWorkbookAnalysisResponse;
import com.rodrilang.librarymanager.importer.price.configuration.service.PriceListWorkbookAnalyzer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/admin/catalog-imports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogImportController {
    private final CatalogImportService importService;
    private final CatalogImportFormatService formatService;
    private final PriceListWorkbookAnalyzer analyzer;

    @PostMapping(value="/analyze", consumes="multipart/form-data")
    public PriceListWorkbookAnalysisResponse analyze(@RequestPart("file") MultipartFile file){ return analyzer.analyze(file); }

    @GetMapping("/formats") public List<CatalogImportFormatResponse> formats(@RequestParam(required=false) Long providerId){ return formatService.findAll(providerId); }
    @GetMapping("/formats/{id}") public CatalogImportFormatResponse format(@PathVariable Long id){ return formatService.findById(id); }
    @PostMapping("/formats") @ResponseStatus(HttpStatus.CREATED)
    public CatalogImportFormatResponse createFormat(@Valid @RequestBody CatalogImportFormatRequest request){ return formatService.create(request); }
    @PutMapping("/formats/{id}") public CatalogImportFormatResponse updateFormat(@PathVariable Long id,@Valid @RequestBody CatalogImportFormatRequest request){ return formatService.update(id,request); }

    @PostMapping(consumes="multipart/form-data") @ResponseStatus(HttpStatus.ACCEPTED)
    public CatalogImportStartResponse start(@RequestParam Long providerId,@RequestParam Long formatId,@RequestPart("file") MultipartFile file){ return importService.start(providerId,formatId,file); }
    @GetMapping("/{id}") public CatalogImportJobResponse detail(@PathVariable Long id){ return importService.find(id); }
    @GetMapping public Page<CatalogImportJobResponse> history(Pageable pageable){ return importService.history(pageable); }
    @PostMapping("/{id}/cancel") public CatalogImportJobResponse cancel(@PathVariable Long id){ return importService.cancel(id); }
}
