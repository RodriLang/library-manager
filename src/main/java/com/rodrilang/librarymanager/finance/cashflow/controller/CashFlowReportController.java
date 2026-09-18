package com.rodrilang.librarymanager.finance.cashflow.controller;

import com.rodrilang.librarymanager.finance.cashflow.dto.response.CashFlowReportResponse;
import com.rodrilang.librarymanager.finance.cashflow.service.CashFlowReportService;
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
@RequestMapping("/api/reports/cash-flow")
@RequiredArgsConstructor
@Tag(name = "Reportes - Flujo de caja", description = "Entradas por ventas y pagos efectivos a proveedores")
public class CashFlowReportController {

    private final CashFlowReportService service;

    @GetMapping
    public ResponseEntity<CashFlowReportResponse> get(
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
