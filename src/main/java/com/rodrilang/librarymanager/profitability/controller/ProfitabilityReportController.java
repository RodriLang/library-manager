package com.rodrilang.librarymanager.profitability.controller;

import com.rodrilang.librarymanager.profitability.dto.response.ProfitabilityReportResponse;
import com.rodrilang.librarymanager.profitability.service.ProfitabilityReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/reports/profitability")
@RequiredArgsConstructor
@Tag(name = "Reportes - Rentabilidad", description = "Rentabilidad histórica de ventas")
public class ProfitabilityReportController {

    private final ProfitabilityReportService service;

    @GetMapping
    public ResponseEntity<ProfitabilityReportResponse> get(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        return ResponseEntity.ok(service.get(from, to));
    }
}
