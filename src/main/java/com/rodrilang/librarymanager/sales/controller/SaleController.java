package com.rodrilang.librarymanager.sales.controller;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.sales.dto.SaleFilter;
import com.rodrilang.librarymanager.sales.dto.request.CancelSaleRequest;
import com.rodrilang.librarymanager.sales.dto.request.CreateSaleRequest;
import com.rodrilang.librarymanager.sales.dto.response.SaleDetailResponse;
import com.rodrilang.librarymanager.sales.dto.response.SaleResponse;
import com.rodrilang.librarymanager.sales.model.SaleOrigin;
import com.rodrilang.librarymanager.sales.model.SaleStatus;
import com.rodrilang.librarymanager.sales.service.SaleCommandService;
import com.rodrilang.librarymanager.sales.service.SaleQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
@Tag(name = "Ventas", description = "Registro y consulta de ventas")
public class SaleController {

    private final SaleCommandService commandService;
    private final SaleQueryService queryService;

    @PostMapping
    public ResponseEntity<SaleDetailResponse> create(
            @Valid @RequestBody CreateSaleRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(commandService.create(request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<SaleResponse>> findAll(
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(required = false) SaleOrigin origin,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @ParameterObject
            @PageableDefault(
                    size = 20,
                    sort = "soldAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        SaleFilter filter = new SaleFilter(status, origin, from, to);

        return ResponseEntity.ok(
                PageResponse.of(queryService.findAll(filter, pageable))
        );
    }

    @GetMapping("/{saleId}")
    public ResponseEntity<SaleDetailResponse> findById(
            @PathVariable Long saleId
    ) {
        return ResponseEntity.ok(queryService.findById(saleId));
    }

    @PostMapping("/{saleId}/cancel")
    public ResponseEntity<SaleDetailResponse> cancel(
            @PathVariable Long saleId,
            @Valid @RequestBody(required = false) CancelSaleRequest request
    ) {
        return ResponseEntity.ok(commandService.cancel(saleId, request));
    }
}
