package com.rodrilang.librarymanager.importer.price.controller;

import com.rodrilang.librarymanager.importer.price.dto.PriceListImportJobStatusResponse;
import com.rodrilang.librarymanager.importer.price.dto.PriceListImportStartResponse;
import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import com.rodrilang.librarymanager.importer.price.service.PriceListImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/price-lists")
@RequiredArgsConstructor
public class PriceListImportController {

    private final PriceListImportService priceListImportService;

    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<PriceListImportStartResponse> importPriceList(
            @RequestParam PriceListSource priceListSource,
            @RequestParam MultipartFile file,
            @RequestParam LocalDate validFrom,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return ResponseEntity.accepted().body(
                priceListImportService.startImport(priceListSource, file, validFrom, idempotencyKey)
        );
    }

    @GetMapping("/imports/{jobId}/status")
    public ResponseEntity<PriceListImportJobStatusResponse> getImportStatus(
            @PathVariable Long jobId
    ) {
        return ResponseEntity.ok(
                priceListImportService.getStatus(jobId)
        );
    }
}