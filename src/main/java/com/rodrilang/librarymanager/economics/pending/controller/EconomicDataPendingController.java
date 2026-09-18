package com.rodrilang.librarymanager.economics.pending.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.economics.pending.dto.response.EconomicDataPendingItemResponse;
import com.rodrilang.librarymanager.economics.pending.dto.response.EconomicDataPendingSummaryResponse;
import com.rodrilang.librarymanager.economics.pending.model.EconomicDataPendingReason;
import com.rodrilang.librarymanager.economics.pending.service.EconomicDataPendingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/economic-data/pending")
@RequiredArgsConstructor
@Tag(name = "Datos económicos pendientes", description = "Información incompleta que limita costos, reposición o futuras compras")
public class EconomicDataPendingController {

    private final EconomicDataPendingService service;

    @GetMapping("/summary")
    public ResponseEntity<EconomicDataPendingSummaryResponse> summary() {
        return ResponseEntity.ok(service.summary());
    }

    @GetMapping
    public ResponseEntity<PageResponse<EconomicDataPendingItemResponse>> findPending(
            @RequestParam(required = false) EconomicDataPendingReason reason,
            @RequestParam(required = false) String q,
            @ParameterObject
            @PageableDefault(size = 50) Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.of(
                service.findPending(reason, q, pageable)
        ));
    }
}
