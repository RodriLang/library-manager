package com.rodrilang.librarymanager.importer.price.controller;

import com.rodrilang.librarymanager.importer.price.dto.PriceListImportResult;
import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/price-lists")
@RequiredArgsConstructor
public class PriceListImportController {

    private final PriceListImportService priceListImportService;

    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<PriceListImportResult> importPriceList(
            @RequestParam PriceListSource priceListSource,
            @RequestParam MultipartFile file
    ) {
        return ResponseEntity.ok(
                priceListImportService.importPriceList(priceListSource, file)
        );
    }
}