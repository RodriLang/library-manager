package com.rodrilang.librarymanager.inventory.pricing.controller;

import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListWorkbookAnalysisResponse;
import com.rodrilang.librarymanager.importer.price.configuration.service.PriceListWorkbookAnalyzer;
import com.rodrilang.librarymanager.inventory.pricing.dto.BookstorePriceListFormatRequest;
import com.rodrilang.librarymanager.inventory.pricing.dto.BookstorePriceListFormatResponse;
import com.rodrilang.librarymanager.inventory.pricing.service.BookstorePriceListFormatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/price-list-formats")
@RequiredArgsConstructor
public class BookstorePriceListFormatController {

    private final BookstorePriceListFormatService service;
    private final PriceListWorkbookAnalyzer workbookAnalyzer;

    @GetMapping
    public List<BookstorePriceListFormatResponse> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<BookstorePriceListFormatResponse> create(
            @Valid @RequestBody BookstorePriceListFormatRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public BookstorePriceListFormatResponse update(
            @PathVariable Long id,
            @Valid @RequestBody BookstorePriceListFormatRequest request
    ) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/analyze-template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PriceListWorkbookAnalysisResponse analyze(@RequestPart("file") MultipartFile file) {
        return workbookAnalyzer.analyze(file);
    }
}
